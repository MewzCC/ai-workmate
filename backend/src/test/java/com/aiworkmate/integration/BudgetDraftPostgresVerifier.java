package com.aiworkmate.integration;

import com.aiworkmate.dto.BudgetPlanRequest;
import com.aiworkmate.mapper.BudgetPlanMapper;
import com.aiworkmate.mapper.BudgetTransactionMapper;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BudgetServiceImpl;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.model.BudgetAgentDraftCommand;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Real PostgreSQL proof for one tenant-scoped budget draft and atomic audit. */
final class BudgetDraftPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long owner = jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,display_name,tenant_id,status,employment_status)
                VALUES ('budget-agent-owner','test-only','budget-owner@example.invalid','预算负责人',?,1,'ACTIVE')
                RETURNING id
                """, Long.class, tenant);

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(BudgetPlanMapper.class, BudgetTransactionMapper.class,
                UserMapper.class, BusinessAuditLogMapper.class)) configuration.addMapper(mapper);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(owner)).thenReturn(new ResolvedUserAccess(
                owner, "budget-agent-owner", tenant, "FINANCE_ADMIN", List.of("FINANCE_ADMIN"),
                List.of("route:budget", "budget:manage"), List.of("TENANT"), 1L));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));

        var created = tx.execute(status -> service.create(owner, request("AGENT-BUDGET-1", owner)));
        assertThat(created).isNotNull();
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.version()).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM budget_plan
                WHERE tenant_id=? AND budget_code='AGENT-BUDGET-1'
                  AND status='DRAFT' AND occupied_amount=0 AND spent_amount=0
                """, Integer.class, tenant)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM budget_transaction
                WHERE tenant_id=? AND budget_id=? AND transaction_type='CREATED'
                """, Integer.class, tenant, created.id())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE tenant_id=? AND resource_type='BUDGET' AND resource_id=? AND action='CREATE'
                """, Integer.class, tenant, created.id().toString())).isOne();

        var updated = tx.execute(status -> service.updateAgentDraft(
                owner, created.id(), created.version(), update("Agent 预算草稿二期", owner)));
        assertThat(updated).isNotNull();
        assertThat(updated.status()).isEqualTo("DRAFT");
        assertThat(updated.version()).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM budget_plan
                WHERE tenant_id=? AND id=? AND name='Agent 预算草稿二期'
                  AND status='DRAFT' AND version=1
                """, Integer.class, tenant, created.id())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM budget_transaction
                WHERE tenant_id=? AND budget_id=? AND transaction_type='UPDATED'
                """, Integer.class, tenant, created.id())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE tenant_id=? AND resource_type='BUDGET' AND resource_id=? AND action='UPDATE'
                """, Integer.class, tenant, created.id().toString())).isOne();

        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failing = service(session, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status ->
                failing.create(owner, request("AGENT-BUDGET-ROLLBACK", owner))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM budget_plan WHERE tenant_id=? AND budget_code='AGENT-BUDGET-ROLLBACK'",
                Integer.class, tenant)).isZero();
        assertThatThrownBy(() -> tx.execute(status -> failing.updateAgentDraft(
                owner, created.id(), updated.version(), update("不应保存", owner))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM budget_plan WHERE tenant_id=? AND id=? AND name='Agent 预算草稿二期' AND version=1",
                Integer.class, tenant, created.id())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM budget_transaction
                WHERE tenant_id=? AND budget_id=? AND transaction_type='UPDATED'
                """, Integer.class, tenant, created.id())).isOne();
    }

    private static BudgetServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new BudgetServiceImpl(
                session.getMapper(BudgetPlanMapper.class), session.getMapper(BudgetTransactionMapper.class),
                session.getMapper(UserMapper.class), access, audit,
                mock(NotificationService.class), mock(MessageSource.class));
    }

    private static BudgetPlanRequest request(String code, Long owner) {
        return new BudgetPlanRequest(code, "Agent 预算草稿", 2027, owner,
                new BigDecimal("100000.00"), "CNY", 80, "受控创建", null);
    }

    private static BudgetAgentDraftCommand update(String name, Long owner) {
        return new BudgetAgentDraftCommand(name, 2027, owner,
                new BigDecimal("120000.00"), "CNY", 85, "受控更新");
    }
}
