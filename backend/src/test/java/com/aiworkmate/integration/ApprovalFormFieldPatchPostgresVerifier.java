package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalFormFieldPatchRequest;
import com.aiworkmate.agent.registry.AgentApprovalFormFieldPatchToolDefinitions;
import com.aiworkmate.agent.registry.ToolDefinition;
import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.ApprovalEngineService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.ApprovalEngineServiceImpl;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Real mappers and annotation-driven service/audit transactions, isolated migrated schemas only. */
final class ApprovalFormFieldPatchPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties(); properties.setProperty("currentSchema", schema);
        properties.setProperty("options", "-c lock_timeout=5000 -c statement_timeout=10000");
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        var mapper = new ObjectMapper(); ToolDefinition definition;
        try (var beans = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            beans.registerBean(ObjectMapper.class, () -> mapper); beans.register(AgentApprovalFormFieldPatchToolDefinitions.class); beans.refresh();
            definition = beans.getBean(ToolDefinition.class);
        }
        var registration = jdbc.queryForMap("SELECT * FROM agent_tool WHERE tenant_id IS NULL AND code='approval.form.patchDraftField'");
        assertThat(registration.get("enabled")).isEqualTo(false);
        assertThat(registration.get("risk_level")).isEqualTo("L1");
        assertThat(registration.get("side_effect")).isEqualTo("SINGLE_WRITE");
        assertThat(registration.get("confirmation_policy")).isEqualTo("EXPLICIT");
        assertThat(registration.get("retry_policy")).isEqualTo("NEVER");
        assertThat(registration.get("schema_hash")).isEqualTo(definition.schemaHash())
                .isEqualTo("sha256:2d7f9c12e2c7bc7a72f986c077ba8de9a5b81f69d23342f1e8408959f8369744");
        assertThat(mapper.readTree(registration.get("parameters_schema").toString())).isEqualTo(definition.inputSchema());
        assertThat(mapper.readTree(registration.get("output_schema").toString())).isEqualTo(definition.outputSchema());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rbac_role_permission p WHERE p.permission_code='agent:tool:approval.form.patchDraftField' AND NOT EXISTS (SELECT 1 FROM rbac_role_permission r WHERE r.tenant_id=p.tenant_id AND r.role_code=p.role_code AND r.permission_code='approval:manage')", Long.class)).isZero();
        long tenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES ('FORM_PATCH_A','Fixture') RETURNING id", Long.class);
        long otherTenant = jdbc.queryForObject("INSERT INTO tenant(code,name) VALUES ('FORM_PATCH_B','Fixture') RETURNING id", Long.class);
        long actor = jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id,status) VALUES ('form-patch-actor','test-only','form-patch@example.invalid',?,1) RETURNING id", Long.class, tenant);
        String schemaJson = "{\"fields\":[{\"name\":\"amount\",\"label\":\"Amount\",\"type\":\"money\",\"min\":0.123456789012345678901},{\"key\":\"reason\",\"label\":\"Reason\",\"type\":\"text\"}]}";
        long form = form(jdbc, tenant, schemaJson); long other = form(jdbc, otherTenant, schemaJson);
        var configuration = new MybatisConfiguration(); configuration.setMapUnderscoreToCamelCase(true);
        for (var type : List.of(ApprovalFormMapper.class, ApprovalProcessMapper.class, ApprovalRuleMapper.class, UserMapper.class, BusinessAuditLogMapper.class)) configuration.addMapper(type);
        var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(datasource); factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject()); var access = mock(UserAccessService.class);
        var realForms = session.getMapper(ApprovalFormMapper.class);
        var readBarrier = new AtomicReference<CyclicBarrier>();
        var afterRead = new AtomicReference<Runnable>();
        var observedVersions = new java.util.concurrent.ConcurrentLinkedQueue<Integer>();
        var forms = mock(ApprovalFormMapper.class, org.mockito.AdditionalAnswers.delegatesTo(realForms));
        doAnswer(call -> {
            var row = realForms.selectOne(call.getArgument(0));
            var concurrentChange = afterRead.getAndSet(null);
            if (concurrentChange != null) concurrentChange.run();
            var barrier = readBarrier.get();
            if (barrier != null) {
                observedVersions.add(row.getVersion());
                barrier.await(10, TimeUnit.SECONDS);
            }
            return row;
        }).when(forms).selectOne(any(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class));
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, tenant, "approval:manage"));
        var transactions = new DataSourceTransactionManager(datasource);
        var statusChange = new org.springframework.transaction.support.TransactionTemplate(transactions);
        statusChange.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        var auditTarget = spy(new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class)));
        var auditProxy = new ProxyFactory(auditTarget); auditProxy.setInterfaces(BusinessAuditService.class);
        auditProxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var target = new ApprovalEngineServiceImpl(forms, session.getMapper(ApprovalProcessMapper.class),
                session.getMapper(ApprovalRuleMapper.class), session.getMapper(UserMapper.class), access,
                (BusinessAuditService) auditProxy.getProxy(), new ObjectMapper());
        var proxy = new ProxyFactory(target); proxy.setInterfaces(ApprovalEngineService.class);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var service = (ApprovalEngineService) proxy.getProxy();
        var result = service.patchFormDraftFieldAgent(actor, tenant, form, patch(3, "New reason"));
        assertThat(result.version()).isEqualTo(4); assertThat(result.status()).isEqualTo("DISABLED");
        assertThat(result.formId()).isEqualTo(form); assertThat(result.fieldName()).isEqualTo("reason");
        assertThat(result.label()).isEqualTo("New reason"); assertThat(result.required()).isFalse();
        assertThat(result.placeholder()).isEmpty(); assertThat(result.width()).isEqualTo("full");
        var receipt = mapper.valueToTree(result);
        assertThat(new ToolSchemaValidator().valid(definition.outputSchema(), receipt)).isTrue();
        assertThat(receipt.toString()).doesNotContain("schemaJson", "creatorName", "description");
        var row = jdbc.queryForMap("SELECT * FROM approval_form WHERE id=?", form);
        String saved = row.get("schema_json").toString();
        assertThat(saved).contains("0.123456789012345678901", "\"key\":\"reason\"", "New reason").doesNotContain("\"options\"", "\"width\"");
        assertThat(row.get("form_name")).isEqualTo("Patch fixture");
        assertThat(audits(jdbc, form)).isEqualTo(1);
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, form, patch(3, "Stale")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("VERSION_CONFLICT");

        doAnswer(call -> { call.callRealMethod(); throw new IllegalStateException("fixture audit failure after insert"); })
                .when(auditTarget).recordTransactional(any(), any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, form, patch(4, "Must roll back")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT schema_json FROM approval_form WHERE id=?", String.class, form)).isEqualTo(saved);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_form WHERE id=?", Integer.class, form)).isEqualTo(4);
        assertThat(audits(jdbc, form)).isEqualTo(1);
        doCallRealMethod().when(auditTarget).recordTransactional(any(), any(), any(), any(), any(), any(), any());

        readBarrier.set(new CyclicBarrier(2)); var pool = Executors.newFixedThreadPool(2);
        try {
            var attempts = pool.invokeAll(List.<java.util.concurrent.Callable<Boolean>>of(
                    () -> attempt(service, actor, tenant, form, "Concurrent A"),
                    () -> attempt(service, actor, tenant, form, "Concurrent B")), 20, TimeUnit.SECONDS);
            assertThat(attempts).noneMatch(java.util.concurrent.Future::isCancelled);
            assertThat(List.of(attempts.get(0).get(10, TimeUnit.SECONDS), attempts.get(1).get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(observedVersions).containsExactlyInAnyOrder(4, 4);
        } finally { readBarrier.set(null); pool.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT version FROM approval_form WHERE id=?", Integer.class, form)).isEqualTo(5);
        assertThat(audits(jdbc, form)).isEqualTo(2);
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, other, patch(3, "Foreign")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, otherTenant, "approval:manage"));
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, form, patch(5, "Changed tenant")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, tenant, "approval:read"));
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, form, patch(5, "Revoked")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        when(access.resolveActiveUser(actor)).thenReturn(actor(actor, tenant, "approval:manage"));
        String beforeStatusRace = jdbc.queryForObject("SELECT schema_json FROM approval_form WHERE id=?", String.class, form);
        afterRead.set(() -> statusChange.executeWithoutResult(ignored ->
                assertThat(jdbc.update("UPDATE approval_form SET status='ENABLED' WHERE id=?", form)).isEqualTo(1)));
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, form, patch(5, "Published after read")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT schema_json FROM approval_form WHERE id=?", String.class, form)).isEqualTo(beforeStatusRace);
        assertThat(jdbc.queryForObject("SELECT version FROM approval_form WHERE id=?", Integer.class, form)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_form WHERE id=?", String.class, form)).isEqualTo("ENABLED");
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, form, patch(5, "Published")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        jdbc.update("UPDATE approval_form SET status='DISABLED',deleted=TRUE WHERE id=?", form);
        assertThatThrownBy(() -> service.patchFormDraftFieldAgent(actor, tenant, form, patch(5, "Deleted")))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        assertThat(audits(jdbc, form)).isEqualTo(2);
    }
    private static boolean attempt(ApprovalEngineService service, long actor, long tenant, long form, String label) {
        try { service.patchFormDraftFieldAgent(actor, tenant, form, patch(4, label)); return true; }
        catch (BusinessException conflict) { assertThat(conflict.getErrorCode()).isEqualTo("VERSION_CONFLICT"); return false; }
    }
    private static long audits(JdbcTemplate jdbc, long form) {
        return jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='APPROVAL_FORM' AND resource_id=? AND action='PATCH_FIELD'", Long.class, Long.toString(form));
    }
    private static long form(JdbcTemplate jdbc, long tenant, String json) {
        return jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,version) VALUES (?,'patch_fixture','Patch fixture',?,'DISABLED',3) RETURNING id", Long.class, tenant, json);
    }
    private static ApprovalFormFieldPatchRequest patch(int version, String label) {
        return new ApprovalFormFieldPatchRequest(version, "reason", label, null, null, null);
    }
    private static ResolvedUserAccess actor(long actor, long tenant, String permission) {
        return new ResolvedUserAccess(actor, "fixture", tenant, "EMPLOYEE", List.of("EMPLOYEE"), List.of(permission), List.of("TENANT"), 1L);
    }
}
