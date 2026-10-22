package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.VersionRequest;
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
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

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
        factory.setMapperLocations(
                new ClassPathResource("mapper/ApprovalApplicationMapper.xml"),
                new ClassPathResource("mapper/WorkflowActionLogMapper.xml"));
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, 1L));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        verifyCancellationTransaction(jdbc, session, access, audit, tx, applicant, tenant);
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

        var updatePatch = new ExpenseAgentDraftCommand(
                new BigDecimal("99.50"), null, null, null, null);
        var updateExecutor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> update = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    tx.execute(status -> service.updateAgentDraft(
                            applicant, applicationId, 0, updatePatch));
                    return true;
                } catch (BusinessException error) {
                    assertThat(error.getErrorCode()).isEqualTo("VERSION_CONFLICT");
                    return false;
                }
            };
            var first = updateExecutor.submit(update);
            var second = updateExecutor.submit(update);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            updateExecutor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT version FROM approval_application WHERE id=?",
                Integer.class, applicationId)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT data_json::jsonb ->> 'amount' FROM approval_application WHERE id=?",
                String.class, applicationId)).isEqualTo("99.50");
        assertThat(jdbc.queryForObject(
                "SELECT data_json::jsonb ->> 'reason' FROM approval_application WHERE id=?",
                String.class, applicationId)).isEqualTo("客户拜访");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='DRAFT_UPDATE'
                """, Integer.class, applicationId.toString())).isOne();

        Long withdrawId = pendingExpense(jdbc, tenant, applicant, "expense-withdraw-concurrent");
        var withdrawExecutor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> withdraw = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    tx.execute(status -> service.withdrawAgentApplication(applicant, withdrawId, 0));
                    return true;
                } catch (BusinessException error) {
                    assertThat(error.getErrorCode()).isIn(
                            "VERSION_CONFLICT", "BUSINESS_STATE_INVALID");
                    return false;
                }
            };
            var first = withdrawExecutor.submit(withdraw);
            var second = withdrawExecutor.submit(withdraw);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            withdrawExecutor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM approval_application WHERE id=?", String.class, withdrawId))
                .isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM workflow_task
                WHERE business_type='GENERIC_APPROVAL' AND business_id=?
                """, String.class, withdrawId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='WITHDRAW'
                """, Integer.class, withdrawId.toString())).isOne();

        var reopenExecutor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> reopen = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    tx.execute(status -> service.reopenAgentApplication(applicant, withdrawId, 1));
                    return true;
                } catch (BusinessException error) {
                    assertThat(error.getErrorCode()).isIn(
                            "VERSION_CONFLICT", "BUSINESS_STATE_INVALID");
                    return false;
                }
            };
            var first = reopenExecutor.submit(reopen);
            var second = reopenExecutor.submit(reopen);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            reopenExecutor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM approval_application WHERE id=?", String.class, withdrawId))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject(
                "SELECT version FROM approval_application WHERE id=?", Integer.class, withdrawId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='REOPEN'
                """, Integer.class, withdrawId.toString())).isOne();

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

        Long updateRollbackId = tx.execute(status -> service.createAgentDraft(
                applicant, rollbackCommand, "expense-update-rollback").applicationId());
        assertThatThrownBy(() -> tx.execute(status -> failingService.updateAgentDraft(
                applicant, updateRollbackId, 0,
                new ExpenseAgentDraftCommand(
                        new BigDecimal("30.00"), null, null, null, null))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM approval_application WHERE id=?",
                Integer.class, updateRollbackId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT data_json::jsonb ->> 'amount' FROM approval_application WHERE id=?",
                String.class, updateRollbackId)).isEqualTo("20.00");

        Long withdrawRollbackId = pendingExpense(
                jdbc, tenant, applicant, "expense-withdraw-rollback");
        assertThatThrownBy(() -> tx.execute(status -> failingService.withdrawAgentApplication(
                applicant, withdrawRollbackId, 0)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM approval_application WHERE id=?",
                String.class, withdrawRollbackId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM workflow_task
                WHERE business_type='GENERIC_APPROVAL' AND business_id=?
                """, String.class, withdrawRollbackId)).isEqualTo("PENDING");

        Long reopenRollbackId = pendingExpense(
                jdbc, tenant, applicant, "expense-reopen-rollback");
        tx.executeWithoutResult(status -> service.withdrawAgentApplication(
                applicant, reopenRollbackId, 0));
        assertThatThrownBy(() -> tx.execute(status -> failingService.reopenAgentApplication(
                applicant, reopenRollbackId, 1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM approval_application WHERE id=?",
                String.class, reopenRollbackId)).isEqualTo("WITHDRAWN");
        assertThat(jdbc.queryForObject(
                "SELECT version FROM approval_application WHERE id=?",
                Integer.class, reopenRollbackId)).isOne();

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
        return new ExpenseApplicationServiceImpl(
                approvalService(session, access, audit), session.getMapper(ApprovalApplicationMapper.class), access, objectMapper);
    }

    private static GenericApprovalServiceImpl approvalService(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new GenericApprovalServiceImpl(
                session.getMapper(ApprovalApplicationMapper.class),
                session.getMapper(ApprovalFormMapper.class),
                session.getMapper(ApprovalProcessMapper.class),
                session.getMapper(ApprovalRuleMapper.class),
                session.getMapper(LeaveApplicationMapper.class),
                session.getMapper(UserMapper.class),
                session.getMapper(WorkflowInstanceMapper.class),
                session.getMapper(WorkflowTaskMapper.class),
                session.getMapper(WorkflowActionLogMapper.class),
                access, audit, mock(NotificationService.class), new ObjectMapper().findAndRegisterModules());
    }

    private static void verifyCancellationTransaction(JdbcTemplate jdbc, SqlSessionTemplate session,
            UserAccessService access, BusinessAuditService audit, TransactionTemplate tx, long applicant, long tenant) throws Exception {
        // Directly constructed services do not apply audit propagation; use the actual Spring transaction advice.
        var proxy = new ProxyFactory(audit);
        proxy.setInterfaces(BusinessAuditService.class);
        proxy.addAdvice(new TransactionInterceptor(tx.getTransactionManager(), new AnnotationTransactionAttributeSource()));
        var transactionalAudit = (BusinessAuditService) proxy.getProxy();
        var generic = approvalService(session, access, transactionalAudit);
        var expense = service(session, access, transactionalAudit);
        var draft = tx.execute(status -> expense.createAgentDraft(applicant,
                new ExpenseAgentDraftCommand(null, null, null, null, "测试取消事务"), "cancel-transaction-probe"));
        long id = draft.applicationId();
        var failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("test-only cancellation audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failing = approvalService(session, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status -> failing.cancelAgentDraft(applicant, id, new VersionRequest(0))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?", Integer.class, id)).isZero();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            generic.cancelAgentDraft(applicant, id, new VersionRequest(0));
            throw new IllegalStateException("test-only outer failure after audit");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT completed_at IS NULL FROM approval_application WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE tenant_id=? AND resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='DRAFT_CANCEL'
                """, Integer.class, tenant, Long.toString(id))).isZero();

        var result = tx.execute(status -> generic.cancelAgentDraft(applicant, id, new VersionRequest(0)));
        assertThat(result.status()).isEqualTo("CANCELLED");
        assertThat(result.version()).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE tenant_id=? AND resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='DRAFT_CANCEL'
                """, Integer.class, tenant, Long.toString(id))).isOne();
        assertThatThrownBy(() -> tx.execute(status -> generic.cancelAgentDraft(applicant, id, new VersionRequest(0))))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        long concurrentId = tx.execute(status -> expense.createAgentDraft(applicant,
                new ExpenseAgentDraftCommand(null, null, null, null, "测试并发取消"), "cancel-concurrent-probe")).applicationId();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> cancel = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    tx.execute(status -> generic.cancelAgentDraft(applicant, concurrentId, new VersionRequest(0)));
                    return true;
                } catch (BusinessException error) {
                    assertThat(error.getErrorCode()).isIn("VERSION_CONFLICT", "BUSINESS_STATE_INVALID");
                    return false;
                }
            };
            var first = executor.submit(cancel); var second = executor.submit(cancel);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT version FROM approval_application WHERE id=?", Integer.class, concurrentId)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM approval_application WHERE id=?", String.class, concurrentId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE tenant_id=? AND resource_type='GENERIC_APPROVAL' AND resource_id=? AND action='DRAFT_CANCEL'
                """, Integer.class, tenant, Long.toString(concurrentId))).isOne();
    }

    private static ResolvedUserAccess actor(long userId, long tenantId, long version) {
        return new ResolvedUserAccess(
                userId, "expense-user", tenantId, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("route:approval-start", "approval:create", "approval:withdraw", "approval:reopen", "approval:cancel"),
                List.of("SELF"), version);
    }

    private static Long pendingExpense(
            JdbcTemplate jdbc, long tenantId, long applicantId, String operationKey) {
        Long formId = jdbc.queryForObject("""
                SELECT id FROM approval_form
                WHERE tenant_id=? AND form_key='expense-application' AND deleted=FALSE
                """, Long.class, tenantId);
        Long processId = jdbc.queryForObject("""
                SELECT id FROM approval_process
                WHERE tenant_id=? AND process_key='expense-single-approval' AND deleted=FALSE
                """, Long.class, tenantId);
        Long definitionId = jdbc.queryForObject("""
                SELECT id FROM workflow_definition
                WHERE tenant_id=? AND business_type='GENERIC_APPROVAL' AND enabled=TRUE
                ORDER BY version DESC LIMIT 1
                """, Long.class, tenantId);
        Long applicationId = jdbc.queryForObject("""
                INSERT INTO approval_application(
                    tenant_id,applicant_user_id,form_id,process_id,form_key,form_name,title,
                    data_json,agent_operation_key,status,version,submitted_at)
                VALUES (?, ?, ?, ?, 'expense-application', '费用报销单', '费用报销',
                    '{}', ?, 'PENDING', 0, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, tenantId, applicantId, formId, processId, operationKey);
        Long instanceId = jdbc.queryForObject("""
                INSERT INTO workflow_instance(
                    tenant_id,definition_id,business_type,business_id,applicant_id,status)
                VALUES (?, ?, 'GENERIC_APPROVAL', ?, ?, 'RUNNING')
                RETURNING id
                """, Long.class, tenantId, definitionId, applicationId, applicantId);
        jdbc.update("UPDATE approval_application SET workflow_instance_id=? WHERE id=?",
                instanceId, applicationId);
        jdbc.update("""
                INSERT INTO workflow_task(
                    tenant_id,instance_id,business_type,business_id,assignee_user_id,status)
                VALUES (?, ?, 'GENERIC_APPROVAL', ?, ?, 'PENDING')
                """, tenantId, instanceId, applicationId, applicantId);
        return applicationId;
    }
}
