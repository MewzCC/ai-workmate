package com.aiworkmate.integration;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AssetLedgerMapper;
import com.aiworkmate.mapper.AssetOperationMapper;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.mapper.MeetingBookingMapper;
import com.aiworkmate.mapper.MeetingRoomMapper;
import com.aiworkmate.mapper.SealUsageMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.VisitorBookingMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.AdminAssetsServiceImpl;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.service.model.SealAgentUseCommand;
import com.aiworkmate.service.model.SealAgentUseReceipt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

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

/** Real PostgreSQL proof for one self-owned actual seal-use registration. */
final class SealRegisterUsePostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long department = jdbc.queryForObject(
                "SELECT id FROM department WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenant);
        Long applicant = insertUser(jdbc, tenant, department, "seal-use-applicant");
        Long otherUser = insertUser(jdbc, tenant, department, "seal-use-other");
        Long usageId = insertApprovedUsage(jdbc, tenant, applicant, "并发登记");

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(SealUsageMapper.class, UserMapper.class,
                WorkflowInstanceMapper.class, WorkflowTaskMapper.class, WorkflowActionLogMapper.class,
                BusinessAuditLogMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, List.of("seal:register")));
        when(access.resolveActiveUser(otherUser)).thenReturn(
                actor(otherUser, tenant, List.of("seal:register", "seal:register:any")));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        var command = new SealAgentUseCommand(usageId, 2, 2, "现场核对");

        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<SealAgentUseReceipt> register = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.registerSealUseAgent(
                        applicant, command, "seal-use-operation"));
            };
            var first = executor.submit(register);
            var second = executor.submit(register);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM seal_usage WHERE id=?", String.class, usageId)).isEqualTo("USED");
        assertThat(jdbc.queryForObject(
                "SELECT actual_copies FROM seal_usage WHERE id=?", Integer.class, usageId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM seal_usage_operation
                WHERE usage_id=? AND agent_operation_key='seal-use-operation'
                """, Integer.class, usageId)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='SEAL_USAGE' AND resource_id=? AND action='USE'
                """, Integer.class, usageId.toString())).isOne();
        Optional<SealAgentUseReceipt> observed = tx.execute(status -> service.findAgentRegisteredSealUse(
                applicant, command, "seal-use-operation"));
        assertThat(observed).isPresent();
        assertThat(observed.orElseThrow().actualCopies()).isEqualTo(2);

        assertThatThrownBy(() -> tx.execute(status -> service.registerSealUseAgent(
                otherUser, command, "seal-use-substitution")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");

        Long rollbackUsageId = insertApprovedUsage(jdbc, tenant, applicant, "审计回滚");
        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = service(session, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status -> failingService.registerSealUseAgent(
                applicant, new SealAgentUseCommand(rollbackUsageId, 2, 1, null),
                "seal-use-rollback"))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM seal_usage WHERE id=?", String.class, rollbackUsageId))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM seal_usage_operation WHERE agent_operation_key='seal-use-rollback'
                """, Integer.class)).isZero();

        when(access.resolveActiveUser(applicant)).thenReturn(
                actor(applicant, tenant + 100000, List.of("seal:register")));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentRegisteredSealUse(
                applicant, command, "seal-use-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, List.of()));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentRegisteredSealUse(
                applicant, command, "seal-use-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }

    private static Long insertUser(JdbcTemplate jdbc, Long tenant, Long department, String username) {
        return jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id,department_id,status)
                VALUES (?, 'test-only', ?, ?, ?, 1) RETURNING id
                """, Long.class, username, username + "@example.invalid", tenant, department);
    }

    private static Long insertApprovedUsage(
            JdbcTemplate jdbc, Long tenant, Long applicant, String title) {
        return jdbc.queryForObject("""
                INSERT INTO seal_usage(tenant_id,applicant_user_id,seal_type,document_title,
                    usage_reason,copies,status,version,submitted_at,completed_at)
                VALUES (?,?,'OFFICIAL',?,'测试用印',2,'APPROVED',2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, tenant, applicant, title);
    }

    private static ResolvedUserAccess actor(long userId, long tenantId, List<String> permissions) {
        return new ResolvedUserAccess(userId, "applicant", tenantId, "EMPLOYEE", List.of("EMPLOYEE"),
                permissions, List.of("SELF"), 1L);
    }

    private static AdminAssetsServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new AdminAssetsServiceImpl(
                mock(AssetLedgerMapper.class), mock(AssetOperationMapper.class), mock(AccessControlMapper.class),
                mock(MeetingRoomMapper.class), mock(MeetingBookingMapper.class),
                mock(VisitorBookingMapper.class), session.getMapper(SealUsageMapper.class),
                session.getMapper(UserMapper.class), session.getMapper(WorkflowInstanceMapper.class),
                session.getMapper(WorkflowTaskMapper.class), session.getMapper(WorkflowActionLogMapper.class),
                access, audit, mock(NotificationService.class));
    }
}
