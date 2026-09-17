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
import com.aiworkmate.service.model.VisitorAgentVisitCommand;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
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

/** Real PostgreSQL proof for one related-user Agent visitor check-in. */
final class VisitorCheckInPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long department = jdbc.queryForObject(
                "SELECT id FROM department WHERE tenant_id=? ORDER BY id LIMIT 1", Long.class, tenant);
        Long applicant = insertUser(jdbc, tenant, department, "visitor-checkin-applicant");
        Long unrelated = insertUser(jdbc, tenant, department, "visitor-checkin-unrelated");
        Long bookingId = insertApprovedBooking(jdbc, tenant, applicant, "并发签到访客");

        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        for (var mapper : List.of(VisitorBookingMapper.class, BusinessAuditLogMapper.class)) {
            configuration.addMapper(mapper);
        }
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, List.of("visitor:register")));
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var service = service(session, access, audit);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        var command = new VisitorAgentVisitCommand(bookingId, 2, "已核验证件");

        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> checkIn = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.checkInVisitorAgent(
                        applicant, command, "visitor-checkin-operation").version());
            };
            var first = executor.submit(checkIn);
            var second = executor.submit(checkIn);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(3);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(3);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM visitor_booking WHERE id=?", String.class, bookingId))
                .isEqualTo("CHECKED_IN");
        assertThat(jdbc.queryForObject(
                "SELECT version FROM visitor_booking WHERE id=?", Integer.class, bookingId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM visitor_visit_operation
                WHERE agent_operation_key='visitor-checkin-operation'
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='VISITOR_BOOKING' AND resource_id=? AND action='CHECK_IN'
                """, Integer.class, bookingId.toString())).isOne();
        var observed = tx.execute(status -> service.findAgentVisitorCheckIn(
                applicant, command, "visitor-checkin-operation"));
        assertThat(observed).isPresent();
        assertThat(observed.orElseThrow().status()).isEqualTo("CHECKED_IN");

        var arrivalCommand = new VisitorAgentVisitCommand(bookingId, 3, "前台确认到访");
        var arrivalExecutor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> arrive = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.markVisitorArrivedAgent(
                        applicant, arrivalCommand, "visitor-arrival-operation").version());
            };
            var first = arrivalExecutor.submit(arrive);
            var second = arrivalExecutor.submit(arrive);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(4);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(4);
        } finally {
            arrivalExecutor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM visitor_booking WHERE id=?", String.class, bookingId))
                .isEqualTo("VISITED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM visitor_visit_operation
                WHERE agent_operation_key='visitor-arrival-operation' AND operation_type='ARRIVE'
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='VISITOR_BOOKING' AND resource_id=? AND action='ARRIVE'
                """, Integer.class, bookingId.toString())).isOne();
        var observedArrival = tx.execute(status -> service.findAgentVisitorArrival(
                applicant, arrivalCommand, "visitor-arrival-operation"));
        assertThat(observedArrival).isPresent();
        assertThat(observedArrival.orElseThrow().status()).isEqualTo("VISITED");

        var leaveCommand = new VisitorAgentVisitCommand(bookingId, 4, "前台确认离场");
        var leaveExecutor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Integer> leave = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.leaveVisitorAgent(
                        applicant, leaveCommand, "visitor-leave-operation").version());
            };
            var first = leaveExecutor.submit(leave);
            var second = leaveExecutor.submit(leave);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(5);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(5);
        } finally {
            leaveExecutor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT status FROM visitor_booking WHERE id=?", String.class, bookingId))
                .isEqualTo("LEFT");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM visitor_visit_operation
                WHERE agent_operation_key='visitor-leave-operation' AND operation_type='LEAVE'
                """, Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_audit_log
                WHERE resource_type='VISITOR_BOOKING' AND resource_id=? AND action='LEAVE'
                """, Integer.class, bookingId.toString())).isOne();
        var observedLeave = tx.execute(status -> service.findAgentVisitorLeave(
                applicant, leaveCommand, "visitor-leave-operation"));
        assertThat(observedLeave).isPresent();
        assertThat(observedLeave.orElseThrow().status()).isEqualTo("LEFT");

        Long rollbackBooking = insertApprovedBooking(jdbc, tenant, applicant, "回滚签到访客");
        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = service(session, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status -> failingService.checkInVisitorAgent(
                applicant, new VisitorAgentVisitCommand(rollbackBooking, 2, null),
                "visitor-checkin-rollback"))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM visitor_booking WHERE id=?", String.class, rollbackBooking))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM visitor_visit_operation
                WHERE agent_operation_key='visitor-checkin-rollback'
                """, Integer.class)).isZero();

        Long arrivalRollbackBooking = insertApprovedBooking(jdbc, tenant, applicant, "回滚到访访客");
        jdbc.update("""
                UPDATE visitor_booking SET status='CHECKED_IN', version=3,
                    checked_in_at=CURRENT_TIMESTAMP WHERE id=?
                """, arrivalRollbackBooking);
        assertThatThrownBy(() -> tx.execute(status -> failingService.markVisitorArrivedAgent(
                applicant, new VisitorAgentVisitCommand(arrivalRollbackBooking, 3, null),
                "visitor-arrival-rollback"))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM visitor_booking WHERE id=?", String.class, arrivalRollbackBooking))
                .isEqualTo("CHECKED_IN");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM visitor_visit_operation
                WHERE agent_operation_key='visitor-arrival-rollback'
                """, Integer.class)).isZero();

        Long leaveRollbackBooking = insertApprovedBooking(jdbc, tenant, applicant, "回滚离场访客");
        jdbc.update("""
                UPDATE visitor_booking SET status='VISITED', version=4,
                    checked_in_at=CURRENT_TIMESTAMP, visited_at=CURRENT_TIMESTAMP WHERE id=?
                """, leaveRollbackBooking);
        assertThatThrownBy(() -> tx.execute(status -> failingService.leaveVisitorAgent(
                applicant, new VisitorAgentVisitCommand(leaveRollbackBooking, 4, null),
                "visitor-leave-rollback"))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM visitor_booking WHERE id=?", String.class, leaveRollbackBooking))
                .isEqualTo("VISITED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM visitor_visit_operation
                WHERE agent_operation_key='visitor-leave-rollback'
                """, Integer.class)).isZero();

        when(access.resolveActiveUser(unrelated)).thenReturn(actor(
                unrelated, tenant, List.of("visitor:register", "visitor:register:any")));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentVisitorLeave(
                unrelated, leaveCommand, "visitor-leave-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_FORBIDDEN");
        when(access.resolveActiveUser(applicant)).thenReturn(actor(applicant, tenant, List.of()));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentVisitorLeave(
                applicant, leaveCommand, "visitor-leave-operation")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }

    private static Long insertUser(JdbcTemplate jdbc, Long tenant, Long department, String username) {
        return jdbc.queryForObject("""
                INSERT INTO app_user(username,password,email,tenant_id,department_id,status)
                VALUES (?, 'test-only', ?, ?, ?, 1) RETURNING id
                """, Long.class, username, username + "@example.invalid", tenant, department);
    }

    private static Long insertApprovedBooking(
            JdbcTemplate jdbc, Long tenant, Long applicant, String visitorName) {
        return jdbc.queryForObject("""
                INSERT INTO visitor_booking(
                    tenant_id,applicant_user_id,visitor_name,purpose,host_user_id,
                    expected_visit_at,party_size,status,version,submitted_at,completed_at)
                VALUES (?, ?, ?, '项目交流', ?, CURRENT_TIMESTAMP, 1, 'APPROVED', 2,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, tenant, applicant, visitorName, applicant);
    }

    private static ResolvedUserAccess actor(
            long userId, long tenantId, List<String> permissions) {
        return new ResolvedUserAccess(userId, "operator", tenantId, "EMPLOYEE", List.of("EMPLOYEE"),
                permissions, List.of("SELF"), 1L);
    }

    private static AdminAssetsServiceImpl service(
            SqlSessionTemplate session, UserAccessService access, BusinessAuditService audit) {
        return new AdminAssetsServiceImpl(
                mock(AssetLedgerMapper.class), mock(AssetOperationMapper.class), mock(AccessControlMapper.class),
                mock(MeetingRoomMapper.class), mock(MeetingBookingMapper.class),
                session.getMapper(VisitorBookingMapper.class), mock(SealUsageMapper.class),
                mock(UserMapper.class), mock(WorkflowInstanceMapper.class), mock(WorkflowTaskMapper.class),
                mock(WorkflowActionLogMapper.class), access, audit, mock(NotificationService.class));
    }
}
