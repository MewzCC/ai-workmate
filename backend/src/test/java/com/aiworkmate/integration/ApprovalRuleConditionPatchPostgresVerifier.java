package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalRuleConditionPatchRequest;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.ApprovalEngineService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.ApprovalEngineServiceImpl;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real SQL, optimistic concurrency and annotation-driven audit rollback in isolated migrated schemas. */
final class ApprovalRuleConditionPatchPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema);
        properties.setProperty("options", "-c lock_timeout=5000 -c statement_timeout=10000");
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        long tenant = tenant(jdbc, "RULE_PATCH_A"), otherTenant = tenant(jdbc, "RULE_PATCH_B");
        long actor = jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id,status) VALUES ('rule-patch-actor','test-only','rule-patch@example.invalid',?,1) RETURNING id", Long.class, tenant);
        long own = rule(jdbc, tenant), foreign = rule(jdbc, otherTenant);
        String originalAction = jdbc.queryForObject("SELECT action_json FROM approval_rule WHERE id=?", String.class, own);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(ApprovalFormMapper.class, ApprovalProcessMapper.class, ApprovalRuleMapper.class,
                UserMapper.class, BusinessAuditLogMapper.class)) configuration.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(datasource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject()); var access = mock(UserAccessService.class);
        var realRules = session.getMapper(ApprovalRuleMapper.class);
        var barrier = new AtomicReference<CyclicBarrier>(); var afterRead = new AtomicReference<Runnable>();
        var versions = new java.util.concurrent.ConcurrentLinkedQueue<Integer>();
        var rules = mock(ApprovalRuleMapper.class, org.mockito.AdditionalAnswers.delegatesTo(realRules));
        doAnswer(call -> {
            var row = realRules.selectOne(call.getArgument(0));
            var concurrentChange = afterRead.getAndSet(null);
            if (concurrentChange != null) concurrentChange.run();
            var rendezvous = barrier.get();
            if (rendezvous != null) { versions.add(row.getVersion()); rendezvous.await(10, TimeUnit.SECONDS); }
            return row;
        }).when(rules).selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, tenant, "approval:manage"));
        var transactions = new DataSourceTransactionManager(datasource);
        var auditTarget = spy(new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class)));
        var auditProxy = new ProxyFactory(auditTarget); auditProxy.setInterfaces(BusinessAuditService.class);
        auditProxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var target = new ApprovalEngineServiceImpl(session.getMapper(ApprovalFormMapper.class), session.getMapper(ApprovalProcessMapper.class),
                rules, session.getMapper(UserMapper.class), access, (BusinessAuditService) auditProxy.getProxy(), new com.fasterxml.jackson.databind.ObjectMapper());
        var proxy = new ProxyFactory(target); proxy.setInterfaces(ApprovalEngineService.class);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var service = (ApprovalEngineService) proxy.getProxy();
        var result = service.patchRuleDraftConditionAgent(actor, tenant, own, patch(3, "ANNUAL"));
        assertThat(result.ruleId()).isEqualTo(own); assertThat(result.version()).isEqualTo(4);
        assertThat(result.conditionNumber()).isEqualTo(2); assertThat(result.value()).isEqualTo("ANNUAL");
        String saved = condition(jdbc, own);
        assertThat(saved).contains("0.123456789012345678901", "ANNUAL", "\"logic\":\"OR\"");
        assertThat(jdbc.queryForMap("SELECT rule_key,rule_name,rule_type,priority,description,action_json,status FROM approval_rule WHERE id=?", own))
                .containsEntry("rule_key", "condition_patch_fixture").containsEntry("rule_name", "Keep name")
                .containsEntry("rule_type", "AMOUNT_THRESHOLD").containsEntry("priority", Integer.MAX_VALUE)
                .containsEntry("description", "Keep description").containsEntry("action_json", originalAction).containsEntry("status", "DISABLED");
        assertThat(audits(jdbc, own)).isEqualTo(1);
        denied("VERSION_CONFLICT", () -> service.patchRuleDraftConditionAgent(actor, tenant, own, patch(3, "Stale")));

        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("fixture audit failure"); })
                .when(auditTarget).recordTransactional(any(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.patchRuleDraftConditionAgent(actor, tenant, own, patch(4, "ROLLBACK")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(condition(jdbc, own)).isEqualTo(saved);
        assertThat(version(jdbc, own)).isEqualTo(4); assertThat(audits(jdbc, own)).isEqualTo(1);
        doCallRealMethod().when(auditTarget).recordTransactional(any(), any(), any(), any(), any(), any(), any());

        barrier.set(new CyclicBarrier(2)); var pool = Executors.newFixedThreadPool(2);
        try {
            var attempts = pool.invokeAll(List.<java.util.concurrent.Callable<Boolean>>of(
                    () -> attempt(service, actor, tenant, own, "A"), () -> attempt(service, actor, tenant, own, "B")), 20, TimeUnit.SECONDS);
            assertThat(attempts).noneMatch(java.util.concurrent.Future::isCancelled);
            assertThat(List.of(attempts.get(0).get(10, TimeUnit.SECONDS), attempts.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(versions).containsExactlyInAnyOrder(4, 4);
        } finally { barrier.set(null); pool.shutdownNow(); }
        assertThat(version(jdbc, own)).isEqualTo(5); assertThat(audits(jdbc, own)).isEqualTo(2);
        denied("RESOURCE_NOT_FOUND", () -> service.patchRuleDraftConditionAgent(actor, tenant, foreign, patch(3, "Foreign")));
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, otherTenant, "approval:manage"));
        denied("PERMISSION_DENIED", () -> service.patchRuleDraftConditionAgent(actor, tenant, own, patch(5, "Tenant changed")));
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, tenant, "approval:read"));
        denied("PERMISSION_DENIED", () -> service.patchRuleDraftConditionAgent(actor, tenant, own, patch(5, "Revoked")));
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, tenant, "approval:manage"));
        var concurrentTransaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        concurrentTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String beforeRace = condition(jdbc, own);
        afterRead.set(() -> concurrentTransaction.executeWithoutResult(ignored ->
                assertThat(jdbc.update("UPDATE approval_rule SET status='ENABLED' WHERE id=?", own)).isEqualTo(1)));
        denied("VERSION_CONFLICT", () -> service.patchRuleDraftConditionAgent(actor, tenant, own, patch(5, "Enabled after read")));
        assertThat(condition(jdbc, own)).isEqualTo(beforeRace); assertThat(version(jdbc, own)).isEqualTo(5);
        denied("BUSINESS_STATE_INVALID", () -> service.patchRuleDraftConditionAgent(actor, tenant, own, patch(5, "Enabled")));
        jdbc.update("UPDATE approval_rule SET status='DISABLED',deleted=TRUE WHERE id=?", own);
        denied("RESOURCE_NOT_FOUND", () -> service.patchRuleDraftConditionAgent(actor, tenant, own, patch(5, "Deleted")));
        assertThat(audits(jdbc, own)).isEqualTo(2);
    }
    private static boolean attempt(ApprovalEngineService service, long actor, long tenant, long rule, String value) {
        try { service.patchRuleDraftConditionAgent(actor, tenant, rule, patch(4, value)); return true; }
        catch (BusinessException conflict) { assertThat(conflict.getErrorCode()).isEqualTo("VERSION_CONFLICT"); return false; }
    }
    private static String condition(JdbcTemplate jdbc, long rule) {
        return jdbc.queryForObject("SELECT condition_json FROM approval_rule WHERE id=?", String.class, rule);
    }
    private static int version(JdbcTemplate jdbc, long rule) {
        return jdbc.queryForObject("SELECT version FROM approval_rule WHERE id=?", Integer.class, rule);
    }
    private static long audits(JdbcTemplate jdbc, long rule) {
        return jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='APPROVAL_RULE' AND resource_id=? AND action='PATCH_CONDITION'", Long.class, Long.toString(rule));
    }
    private static long tenant(JdbcTemplate jdbc, String code) {
        return jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES (?,'Condition patch fixture') RETURNING id", Long.class, code);
    }
    private static long rule(JdbcTemplate jdbc, long tenant) {
        return jdbc.queryForObject("INSERT INTO approval_rule(tenant_id,rule_key,rule_name,rule_type,priority,description,condition_json,action_json,status,version) VALUES (?,'condition_patch_fixture','Keep name','AMOUNT_THRESHOLD',2147483647,'Keep description',?,?,'DISABLED',3) RETURNING id", Long.class,
                tenant, "{\"logic\":\"OR\",\"conditions\":[{\"field\":\"amount\",\"op\":\"gte\",\"value\":0.123456789012345678901},{\"field\":\"leaveType\",\"op\":\"eq\",\"value\":\"SICK\"}]}",
                "{ \"appendNode\":\"FINANCE_REVIEW\", \"enabled\":false }");
    }
    private static ApprovalRuleConditionPatchRequest patch(int version, String value) {
        return new ApprovalRuleConditionPatchRequest(version, 2, value);
    }
    private static ResolvedUserAccess actor(long actor, long tenant, String permission) {
        return new ResolvedUserAccess(actor, "fixture", tenant, "EMPLOYEE", List.of("EMPLOYEE"), List.of(permission), List.of("TENANT"), 1L);
    }
    private static void denied(String code, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(code);
    }
}
