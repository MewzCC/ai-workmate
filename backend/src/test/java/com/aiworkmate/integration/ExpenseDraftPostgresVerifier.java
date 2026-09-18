package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.ApprovalProcessMapper;
import com.aiworkmate.mapper.ApprovalRuleMapper;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.impl.ExpenseApplicationServiceImpl;
import com.aiworkmate.service.impl.GenericApprovalServiceImpl;
import com.aiworkmate.service.model.ExpenseAgentDraftCommand;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real PostgreSQL proof for one self-owned expense draft backed by generic approval. */
final class ExpenseDraftPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long applicant = jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id,status,employment_status)
                VALUES ('expense-draft-applicant','test-only','expense-draft@example.invalid',?,1,'ACTIVE')
                RETURNING id
                """, Long.class, tenant);

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(
                ApprovalApplicationMapper.class, ApprovalFormMapper.class,
                ApprovalProcessMapper.class, ApprovalRuleMapper.class,
                LeaveApplicationMapper.class, UserMapper.class,
                WorkflowInstanceMapper.class, WorkflowTaskMapper.class,
                WorkflowActionLogMapper.class, BusinessAuditLogMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        factory.setMapperLocations(new ClassPathResource("mapper/ApprovalApplicationMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, 1L));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        var command = new ExpenseAgentDraftCommand(
                new BigDecimal("88.50"), "TRAVEL", LocalDate.now().minusDays(1),
                "INV-AGENT-1", "客户拜访");

        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Long> create = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.createAgentDraft(
                        applicant, command, "expense-draft-operation").applicationId());
            };
            var first = executor.submit(create);
            var second = executor.submit(create);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        Long applicationId = jdbc.queryForObject("""
                SELECT id FROM approval_application
                WHERE agent_operation_key='expense-draft-operation'
                """, Long.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM approval_application
                WHERE agent_operation_key='expense-draft-operation'
                  AND form_key='expense-application' AND status='DRAFT' AND version=0
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM workflow_instance
                WHERE business_type='GENERIC_APPROVAL' AND business_id=?
                """, Integer.class, applicationId)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='DRAFT_CREATE'
                """, Integer.class, applicationId.toString())).isOne();
        Optional<?> observed = tx.execute(status -> service.findAgentDraft(
                applicant, command, "expense-draft-operation"));
        assertThat(observed).isPresent();

        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = service(session, access, failingAudit);
        var rollbackCommand = new ExpenseAgentDraftCommand(
                new BigDecimal("20.00"), "MEAL", LocalDate.now(),
                "INV-ROLLBACK", "加班餐费");
        assertThatThrownBy(() -> tx.execute(status -> failingService.createAgentDraft(
                applicant, rollbackCommand, "expense-draft-rollback")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM approval_application
                WHERE agent_operation_key='expense-draft-rollback'
                """, Integer.class)).isZero();

        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant + 100000, 2L));
        Optional<?> crossTenant = tx.execute(status -> service.findAgentDraft(
                applicant, command, "expense-draft-operation"));
        assertThat(crossTenant).isEmpty();
        when(access.resolveActiveUser(applicant)).thenReturn(new ResolvedUserAccess(
                applicant, "expense-user", tenant, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("route:approval-start"), List.of("SELF"), 3L));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentDraft(
                applicant, command, "expense-draft-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }

    private static ExpenseApplicationServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var generic = new GenericApprovalServiceImpl(
                session.getMapper(ApprovalApplicationMapper.class),
                session.getMapper(ApprovalFormMapper.class),
                session.getMapper(ApprovalProcessMapper.class),
                session.getMapper(ApprovalRuleMapper.class),
                session.getMapper(LeaveApplicationMapper.class),
                session.getMapper(UserMapper.class),
                session.getMapper(WorkflowInstanceMapper.class),
                session.getMapper(WorkflowTaskMapper.class),
                session.getMapper(WorkflowActionLogMapper.class),
                access, audit, mock(NotificationService.class), objectMapper);
        return new ExpenseApplicationServiceImpl(
                generic, session.getMapper(ApprovalApplicationMapper.class), access, objectMapper);
    }

    private static ResolvedUserAccess actor(long userId, long tenantId, long version) {
        return new ResolvedUserAccess(
                userId, "expense-user", tenantId, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("route:approval-start", "approval:create"), List.of("SELF"), version);
    }
}
