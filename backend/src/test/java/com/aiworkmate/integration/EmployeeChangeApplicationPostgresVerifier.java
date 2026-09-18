package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.EmployeeChangeMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.impl.EmployeeChangeServiceImpl;
import com.aiworkmate.service.model.EmployeeChangeAgentApplicationCommand;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

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

/** Real PostgreSQL proof for one tenant-scoped, approval-bound employee-change application. */
final class EmployeeChangeApplicationPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long department = jdbc.queryForObject(
                "SELECT id FROM department WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenant);
        Long position = jdbc.queryForObject(
                "SELECT id FROM position WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenant);
        Long reviewer = insertUser(jdbc, tenant, department, position, "change-reviewer", null);
        Long applicant = insertUser(jdbc, tenant, department, position, "change-applicant", reviewer);
        Long employee = insertUser(jdbc, tenant, department, position, "change-employee", reviewer);

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(EmployeeChangeMapper.class, UserMapper.class,
                AccessControlMapper.class, BusinessAuditLogMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, List.of("hr:manage")));
        when(access.resolveActiveUser(reviewer)).thenReturn(actor(reviewer, tenant, List.of("hr:manage")));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        var command = new EmployeeChangeAgentApplicationCommand(
                employee, "TRANSFER", LocalDate.now().plusDays(5),
                department, position, null, reviewer, "项目团队调整");

        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Long> apply = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.createAgent(
                        applicant, command, "employee-change-operation").changeId());
            };
            var first = executor.submit(apply);
            var second = executor.submit(apply);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        Long changeId = jdbc.queryForObject("""
                SELECT id FROM employee_change WHERE agent_operation_key='employee-change-operation'
                """, Long.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM employee_change
                WHERE agent_operation_key='employee-change-operation' AND status='PENDING' AND version=0
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='EMPLOYEE_CHANGE' AND resource_id=? AND action='SUBMIT'
                """, Integer.class, changeId.toString())).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM app_user
                WHERE id=? AND tenant_id=? AND status=1 AND employment_status <> 'OFFBOARDED'
                """, Integer.class, employee, tenant)).isOne();
        Optional<com.aiworkmate.service.model.EmployeeChangeAgentApplicationReceipt> observed =
                tx.execute(status -> service.findAgentApplication(
                        applicant, command, "employee-change-operation"));
        assertThat(observed).isPresent();

        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = service(session, access, failingAudit);
        var rollbackCommand = new EmployeeChangeAgentApplicationCommand(
                employee, "REGULARIZATION", LocalDate.now().plusDays(6),
                null, null, null, reviewer, "转正申请");
        assertThatThrownBy(() -> tx.execute(status -> failingService.createAgent(
                applicant, rollbackCommand, "employee-change-rollback")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM employee_change WHERE agent_operation_key='employee-change-rollback'
                """, Integer.class)).isZero();

        when(access.resolveActiveUser(applicant)).thenReturn(
                actor(applicant, tenant + 100000, List.of("hr:manage")));
        Optional<?> crossTenant = tx.execute(status -> service.findAgentApplication(
                applicant, command, "employee-change-operation"));
        assertThat(crossTenant).isEmpty();
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, List.of()));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentApplication(
                applicant, command, "employee-change-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }

    private static Long insertUser(
            JdbcTemplate jdbc, Long tenant, Long department, Long position,
            String username, Long approverId) {
        return jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id,department_id,position_id,
                                     approver_user_id,status,employment_status)
                VALUES (?, 'test-only', ?, ?, ?, ?, ?, 1, 'ACTIVE') RETURNING id
                """, Long.class, username, username + "@example.invalid", tenant, department,
                position, approverId);
    }

    private static ResolvedUserAccess actor(long userId, long tenantId, List<String> permissions) {
        return new ResolvedUserAccess(userId, "hr-manager", tenantId, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("ALL"), 1L);
    }

    private static EmployeeChangeServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new EmployeeChangeServiceImpl(
                session.getMapper(EmployeeChangeMapper.class), session.getMapper(UserMapper.class),
                session.getMapper(AccessControlMapper.class), access, audit,
                mock(NotificationService.class));
    }
}
