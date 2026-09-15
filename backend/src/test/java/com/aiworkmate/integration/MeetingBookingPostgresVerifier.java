package com.aiworkmate.integration;

import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.entity.MeetingRoom;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.impl.MeetingBookingServiceImpl;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs against the isolated, fully migrated schema owned by the P1 database gate. */
final class MeetingBookingPostgresVerifier {
    static void verify(String url, String username, String password, String schema) throws Exception {
        var datasource = new DriverManagerDataSource(url, username, password);
        var properties = new Properties();
        properties.setProperty("currentSchema", schema);
        datasource.setConnectionProperties(properties);
        var jdbc = new JdbcTemplate(datasource);
        Long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        Long user = jdbc.queryForObject("INSERT INTO app_user(username,password,email,tenant_id) "
                + "VALUES ('meeting-verifier','test-only','meeting-verifier@example.invalid',?) RETURNING id",
                Long.class, tenant);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(MeetingBookingMapper.class);
        configuration.addMapper(MeetingRoomMapper.class);
        configuration.addMapper(UserMapper.class);
        configuration.addMapper(BusinessAuditLogMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(datasource);
        factory.setConfiguration(configuration);
        var session = new SqlSessionTemplate(factory.getObject());
        var rooms = session.getMapper(MeetingRoomMapper.class);
        var bookings = session.getMapper(MeetingBookingMapper.class);
        var users = session.getMapper(UserMapper.class);
        var audit = new BusinessAuditServiceImpl(session.getMapper(BusinessAuditLogMapper.class));
        var access = mock(UserAccessService.class);
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("meeting:book", "meeting:cancel"), List.of("SELF"), 1L));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(datasource));
        var service = new MeetingBookingServiceImpl(bookings, rooms, users, access, audit);
        var room = new MeetingRoom();
        room.setTenantId(tenant); room.setCode("MR-VERIFY"); room.setName("Test room");
        room.setCapacity(10); room.setStatus("OPEN"); room.setDeleted(false);
        tx.execute(status -> rooms.insert(room));
        var start = LocalDateTime.now().plusDays(10).withNano(0);
        var command = new MeetingBookingRequest(room.getId(), "Test meeting", null, start, start.plusHours(1), 2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Long> same = () -> { barrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.createAgent(user, command, "same-operation").id()); };
            var first = executor.submit(same); var second = executor.submit(same);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_booking WHERE agent_operation_key='same-operation'", Integer.class)).isOne();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='MEETING_BOOKING'", Integer.class)).isOne();

            Long bookingId = jdbc.queryForObject("SELECT id FROM meeting_booking WHERE agent_operation_key='same-operation'", Long.class);
            var cancelBarrier = new CyclicBarrier(2);
            Callable<Integer> cancel = () -> { cancelBarrier.await(10, TimeUnit.SECONDS);
                return tx.execute(status -> service.cancelAgent(user, bookingId,
                        new MeetingBookingCancelRequest(0, "changed"), "same-cancellation").version()); };
            var cancelFirst = executor.submit(cancel); var cancelSecond = executor.submit(cancel);
            assertThat(cancelFirst.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(cancelSecond.get(20, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='MEETING_BOOKING' AND action='CANCEL'", Integer.class)).isOne();
            assertThatThrownBy(() -> tx.execute(status -> service.cancelAgent(user, bookingId,
                    new MeetingBookingCancelRequest(0, "different"), "same-cancellation")))
                    .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                    .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");

            var later = new MeetingBookingRequest(room.getId(), "Conflict", null, start.plusHours(2), start.plusHours(3), 2);
            var conflictBarrier = new CyclicBarrier(2);
            Callable<Boolean> conflict = () -> { conflictBarrier.await(10, TimeUnit.SECONDS);
                try { tx.execute(status -> service.createAgent(user, later, Thread.currentThread().getName())); return true; }
                catch (com.aiworkmate.common.BusinessException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo("BUSINESS_STATE_INVALID"); return false;
                } };
            var a = executor.submit(conflict); var b = executor.submit(conflict);
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }

        BusinessAuditService failing = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failing)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = new MeetingBookingServiceImpl(bookings, rooms, users, access, failing);
        var rollback = new MeetingBookingRequest(room.getId(), "Rollback", null, start.plusHours(4), start.plusHours(5), 2);
        assertThatThrownBy(() -> tx.execute(status -> failingService.createAgent(user, rollback, "rollback-operation")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_booking WHERE agent_operation_key='rollback-operation'", Integer.class)).isZero();

        var cancellable = tx.execute(status -> service.createAgent(user, rollback, "cancel-rollback-booking"));
        assertThatThrownBy(() -> tx.execute(status -> failingService.cancelAgent(user, cancellable.id(),
                new MeetingBookingCancelRequest(0, null), "cancel-rollback")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM meeting_booking WHERE id=?", String.class,
                cancellable.id())).isEqualTo("BOOKED");
        assertThat(jdbc.queryForObject("SELECT agent_cancel_operation_key FROM meeting_booking WHERE id=?", String.class,
                cancellable.id())).isNull();
    }
}
