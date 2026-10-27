package com.aiworkmate.integration;

import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.dto.MeetingRoomRequest;
import com.aiworkmate.entity.MeetingRoom;
import com.aiworkmate.mapper.*;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.BusinessAuditServiceImpl;
import com.aiworkmate.service.impl.MeetingBookingServiceImpl;
import com.aiworkmate.service.impl.AdminAssetsServiceImpl;
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
        verifyRoomVersions(jdbc, rooms, bookings, access, audit, tx, user, tenant);
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

        var observed = tx.execute(status -> service.findAgentCreation(user, command, "same-operation"));
        assertThat(observed)
                .hasValueSatisfying(result -> {
                    assertThat(result.status()).isEqualTo("CANCELLED");
                    assertThat(result.version()).isEqualTo(1);
                });
        var cancellation = tx.execute(status -> service.findAgentCancellation(user, observed.orElseThrow().id(),
                new MeetingBookingCancelRequest(0, "changed"), "same-cancellation"));
        assertThat(cancellation).hasValueSatisfying(result -> {
            assertThat(result.status()).isEqualTo("CANCELLED");
            assertThat(result.version()).isEqualTo(1);
        });
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentCancellation(
                user, observed.orElseThrow().id(), new MeetingBookingCancelRequest(0, "different"),
                "same-cancellation"))).isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");
        var missingCancellation = tx.execute(status -> service.findAgentCancellation(user, cancellable.id(),
                new MeetingBookingCancelRequest(0, null), "cancel-rollback"));
        assertThat(missingCancellation).isEmpty();
        var rolledBack = tx.execute(status -> service.findAgentCreation(user, rollback, "rollback-operation"));
        assertThat(rolledBack).isEmpty();
        long foreignUser = user + 100000;
        when(access.resolveActiveUser(foreignUser)).thenReturn(new ResolvedUserAccess(foreignUser, "test", tenant,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("meeting:book"), List.of("SELF"), 1L));
        var otherOwner = tx.execute(status -> service.findAgentCreation(foreignUser, command, "same-operation"));
        assertThat(otherOwner).isEmpty();
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant + 100000,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of("meeting:book"), List.of("SELF"), 1L));
        var otherTenant = tx.execute(status -> service.findAgentCreation(user, command, "same-operation"));
        assertThat(otherTenant).isEmpty();
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant,
                "EMPLOYEE", List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L));
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentCreation(user, command, "same-operation")))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        assertThatThrownBy(() -> tx.execute(status -> service.findAgentCancellation(user,
                observed.orElseThrow().id(), new MeetingBookingCancelRequest(0, "changed"), "same-cancellation")))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }
    private static void verifyRoomVersions(JdbcTemplate jdbc, MeetingRoomMapper rooms, MeetingBookingMapper bookings, UserAccessService access,
            BusinessAuditService audit, TransactionTemplate tx, Long user, Long tenant) throws Exception {
        var writer = new ResolvedUserAccess(user, "test", tenant, "ADMIN", List.of("ADMIN"),
                List.of("meeting:write", "assets:read"), List.of("TENANT"), 1L);
        var original = access.resolveActiveUser(user);
        when(access.resolveActiveUser(user)).thenReturn(writer);
        var service = roomService(rooms, bookings, access, audit);
        verifyAgentRoomOperations(jdbc, service, access, audit, rooms, bookings, tx, user, tenant, writer);
        var created = tx.execute(status -> service.createMeetingRoom(user, roomRequest("Versioned room", null)));
        assertThat(created.version()).isZero();
        var updated = tx.execute(status -> service.updateMeetingRoom(user, created.id(), roomRequest("First update", 0)));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.name()).isEqualTo("First update");
        assertThatThrownBy(() -> tx.execute(status -> service.updateMeetingRoom(user, created.id(), roomRequest("Stale", 0))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        assertThatThrownBy(() -> tx.execute(status -> service.updateMeetingRoom(user, created.id(), roomRequest("Missing", null))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        assertThatThrownBy(() -> tx.execute(status -> service.updateMeetingRoom(user, created.id(), roomRequest("Negative", -1))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<Boolean> update = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    tx.execute(status -> service.updateMeetingRoom(user, created.id(), roomRequest("Concurrent", 1)));
                    return true;
                } catch (com.aiworkmate.common.BusinessException exception) {
                    assertThat(exception.getErrorCode()).isEqualTo("VERSION_CONFLICT");
                    return false;
                }
            };
            var first = executor.submit(update); var second = executor.submit(update);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally { executor.shutdownNow(); }
        assertThat(jdbc.queryForObject("SELECT version FROM meeting_room WHERE id=?", Integer.class, created.id())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM business_audit_log WHERE resource_type='MEETING_ROOM' AND resource_id=? AND action='UPDATE'",
                Integer.class, created.id().toString())).isEqualTo(2);
        BusinessAuditService failingAudit = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failingAudit)
                .recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingService = roomService(rooms, bookings, access, failingAudit);
        assertThatThrownBy(() -> tx.execute(status -> failingService.updateMeetingRoom(user, created.id(), roomRequest("Rollback", 2))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM meeting_room WHERE id=?", String.class, created.id())).isEqualTo("Concurrent");
        assertThat(jdbc.queryForObject("SELECT version FROM meeting_room WHERE id=?", Integer.class, created.id())).isEqualTo(2);
        assertThatThrownBy(() -> tx.execute(status -> failingService.createMeetingRoom(user,
                new MeetingRoomRequest("MR-AUDIT-ROLLBACK", "Rollback", null, 8, null, "OPEN", null, null))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM meeting_room WHERE code='MR-AUDIT-ROLLBACK'", Integer.class)).isZero();
        jdbc.update("""
                INSERT INTO meeting_booking(tenant_id,room_id,organizer_user_id,title,start_at,end_at)
                VALUES (?,?,?,'Active booking',CURRENT_TIMESTAMP + INTERVAL '1 day',CURRENT_TIMESTAMP + INTERVAL '2 days')
                """, tenant, created.id(), user);
        assertThatThrownBy(() -> tx.execute(status -> service.updateMeetingRoom(user, created.id(),
                new MeetingRoomRequest("MR-VERSION", "Closed", null, 8, null, "CLOSED", null, 2))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        assertThat(jdbc.queryForObject("SELECT status FROM meeting_room WHERE id=?", String.class, created.id())).isEqualTo("OPEN");
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant + 100000,
                "ADMIN", List.of("ADMIN"), List.of("meeting:write"), List.of("TENANT"), 1L));
        assertThatThrownBy(() -> tx.execute(status -> service.updateMeetingRoom(user, created.id(), roomRequest("Foreign", 2))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant,
                "ADMIN", List.of("ADMIN"), List.of(), List.of("TENANT"), 1L));
        assertThatThrownBy(() -> tx.execute(status -> service.updateMeetingRoom(user, created.id(), roomRequest("Revoked", 2))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        when(access.resolveActiveUser(user)).thenReturn(original);
    }

    private static void verifyAgentRoomOperations(JdbcTemplate jdbc, AdminAssetsServiceImpl service,
            UserAccessService access, BusinessAuditService audit, MeetingRoomMapper rooms, MeetingBookingMapper bookings,
            TransactionTemplate tx, Long user, Long tenant, ResolvedUserAccess writer) {
        var adapter = new com.aiworkmate.agent.tool.adapter.MeetingRoomAgentDomainToolAdapter(service);
        var actor = new com.aiworkmate.agent.tool.port.ToolActorContext(tenant, user, 3, 4, 0, "trace");
        var fields = new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Fields("MR-AGENT", "Agent room", "Floor 2", 8, "Projector", "OPEN", "Keep me");
        var receipt = tx.execute(status -> adapter.create(actor, fields));
        assertThat(receipt.version()).isZero();
        assertThatThrownBy(() -> tx.execute(status -> adapter.create(actor, fields)))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        var query = new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Query("MR-AGENT", "OPEN", 1, 10);
        var initial = tx.execute(status -> adapter.query(actor, query));
        assertThat(initial.total()).isOne();
        assertThat(initial.items().get(0).version()).isZero();
        var patch = new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Fields(null, "Renamed", null, null, null, null, null);
        var updated = tx.execute(status -> adapter.update(actor, new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Update(receipt.roomId(), 0, patch)));
        assertThat(updated.version()).isEqualTo(1);
        var current = tx.execute(status -> adapter.query(actor, query)).items().get(0);
        assertThat(current.name()).isEqualTo("Renamed");
        assertThat(current.location()).isEqualTo("Floor 2");
        assertThat(current.facilities()).isEqualTo("Projector");
        assertThat(current.remark()).isEqualTo("Keep me");
        assertThat(current.capacity()).isEqualTo(8);
        assertThatThrownBy(() -> tx.execute(status -> adapter.update(actor,
                new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Update(receipt.roomId(), 0, patch))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class).extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        var clear = new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Fields(null, null, null, null, null, null, "");
        tx.execute(status -> adapter.update(actor, new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Update(receipt.roomId(), 1, clear)));
        assertThat(tx.execute(status -> adapter.query(actor, query)).items().get(0).remark()).isEmpty();
        BusinessAuditService failing = mock(BusinessAuditService.class);
        doThrow(new IllegalStateException("audit unavailable")).when(failing).recordTransactional(any(), any(), any(), any(), any(), any(), any());
        var failingAdapter = new com.aiworkmate.agent.tool.adapter.MeetingRoomAgentDomainToolAdapter(roomService(rooms, bookings, access, failing));
        assertThatThrownBy(() -> tx.execute(status -> failingAdapter.update(actor,
                new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Update(receipt.roomId(), 2, patch)))).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT version FROM meeting_room WHERE id=?", Integer.class, receipt.roomId())).isEqualTo(2);
        var invalid = new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Fields(null, "Too long".repeat(30), null, null, null, null, null);
        assertThatThrownBy(() -> tx.execute(status -> adapter.update(actor,
                new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Update(receipt.roomId(), 2, invalid))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class).extracting("errorCode").isEqualTo("REQUEST_INVALID");
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("meeting:read:self"), List.of("SELF"), 1L));
        assertThat(tx.execute(status -> adapter.query(actor, query)).total()).isOne();
        assertThatThrownBy(() -> tx.execute(status -> adapter.update(actor,
                new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Update(receipt.roomId(), 2, patch))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class).extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        when(access.resolveActiveUser(user)).thenReturn(new ResolvedUserAccess(user, "test", tenant + 100000, "ADMIN", List.of("ADMIN"),
                List.of("assets:read", "meeting:write"), List.of("TENANT"), 1L));
        assertThat(tx.execute(status -> adapter.query(actor, query)).total()).isZero();
        assertThatThrownBy(() -> tx.execute(status -> adapter.update(actor,
                new com.aiworkmate.agent.tool.port.MeetingRoomToolPort.Update(receipt.roomId(), 2, patch))))
                .isInstanceOf(com.aiworkmate.common.BusinessException.class).extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        when(access.resolveActiveUser(user)).thenReturn(writer);
    }

    private static MeetingRoomRequest roomRequest(String name, Integer version) {
        return new MeetingRoomRequest("MR-VERSION", name, "Floor 1", 10, null, "OPEN", null, version);
    }

    private static AdminAssetsServiceImpl roomService(MeetingRoomMapper rooms, MeetingBookingMapper bookings, UserAccessService access, BusinessAuditService audit) {
        return new AdminAssetsServiceImpl(mock(AssetLedgerMapper.class), mock(AssetOperationMapper.class),
                mock(AccessControlMapper.class), rooms, bookings, mock(VisitorBookingMapper.class),
                mock(SealUsageMapper.class), mock(UserMapper.class), mock(WorkflowInstanceMapper.class),
                mock(WorkflowTaskMapper.class), mock(WorkflowActionLogMapper.class), access, audit,
                mock(com.aiworkmate.service.NotificationService.class));
    }
}
