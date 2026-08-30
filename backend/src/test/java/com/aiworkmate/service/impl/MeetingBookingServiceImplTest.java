package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingResponse;
import com.aiworkmate.entity.MeetingBooking;
import com.aiworkmate.entity.MeetingRoom;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.MeetingBookingMapper;
import com.aiworkmate.mapper.MeetingRoomMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingBookingServiceImplTest {
    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 1001L;
    private static final long ROOM_ID = 8L;
    private static final long BOOKING_ID = 18L;

    @Mock private MeetingBookingMapper bookingMapper;
    @Mock private MeetingRoomMapper roomMapper;
    @Mock private UserMapper userMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;

    private MeetingBookingServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(MeetingBooking.class, MeetingBookingMapper.class);
        service = new MeetingBookingServiceImpl(bookingMapper, roomMapper, userMapper,
                userAccessService, auditService);
    }

    @Test
    void createsBookingAfterLockingRoomAndCheckingConflict() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(access(false));
        when(roomMapper.lockForBooking(TENANT_ID, ROOM_ID)).thenReturn(room("OPEN", 10));
        when(bookingMapper.selectCount(any())).thenReturn(0L);
        when(bookingMapper.insert(any(MeetingBooking.class))).thenAnswer(invocation -> {
            MeetingBooking booking = invocation.getArgument(0);
            booking.setId(BOOKING_ID);
            return 1;
        });
        when(userMapper.selectById(USER_ID)).thenReturn(user());

        MeetingBookingResponse response = service.create(USER_ID, request(6));

        assertThat(response.id()).isEqualTo(BOOKING_ID);
        assertThat(response.roomName()).isEqualTo("海棠厅");
        assertThat(response.status()).isEqualTo("BOOKED");
        verify(roomMapper).lockForBooking(TENANT_ID, ROOM_ID);
        verify(auditService).recordTransactional(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsOverlappingBooking() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(access(false));
        when(roomMapper.lockForBooking(TENANT_ID, ROOM_ID)).thenReturn(room("OPEN", 10));
        when(bookingMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(USER_ID, request(4)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(bookingMapper, never()).insert(any(MeetingBooking.class));
    }

    @Test
    void rejectsClosedRoomAndCapacityOverflow() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(access(false));
        when(roomMapper.lockForBooking(TENANT_ID, ROOM_ID))
                .thenReturn(room("CLOSED", 10), room("OPEN", 3));

        assertThatThrownBy(() -> service.create(USER_ID, request(2)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.create(USER_ID, request(4)))
                .isInstanceOf(BusinessException.class);
        verify(bookingMapper, never()).selectCount(any());
    }

    @Test
    void ownerCanCancelWithOptimisticVersion() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(access(false));
        MeetingBooking before = booking(TENANT_ID, "BOOKED", 2);
        MeetingBooking after = booking(TENANT_ID, "CANCELLED", 3);
        after.setCancelledByUserId(USER_ID);
        after.setCancelledAt(LocalDateTime.now());
        when(bookingMapper.selectById(BOOKING_ID)).thenReturn(before, after);
        when(bookingMapper.update(any(), any())).thenReturn(1);
        when(roomMapper.selectById(ROOM_ID)).thenReturn(room("OPEN", 10));
        when(userMapper.selectById(USER_ID)).thenReturn(user());

        MeetingBookingResponse response = service.cancel(USER_ID, BOOKING_ID,
                new MeetingBookingCancelRequest(2, "议程调整"));

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.canCancel()).isFalse();
        verify(auditService).recordTransactional(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void crossTenantBookingIsHidden() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(access(false));
        when(bookingMapper.selectById(BOOKING_ID)).thenReturn(booking(2L, "BOOKED", 1));

        assertThatThrownBy(() -> service.cancel(USER_ID, BOOKING_ID,
                new MeetingBookingCancelRequest(1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void versionConflictDoesNotAuditCancellation() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(access(false));
        when(bookingMapper.selectById(BOOKING_ID)).thenReturn(booking(TENANT_ID, "BOOKED", 3));
        when(bookingMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(USER_ID, BOOKING_ID,
                new MeetingBookingCancelRequest(2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        verify(auditService, never()).recordTransactional(any(), any(), any(), any(), any(), any(), any());
    }

    private ResolvedUserAccess access(boolean admin) {
        List<String> permissions = admin
                ? List.of("meeting:book", "meeting:read:self", "meeting:cancel", "meeting:write")
                : List.of("meeting:book", "meeting:read:self", "meeting:cancel");
        return new ResolvedUserAccess(USER_ID, "employee@example.com", TENANT_ID, "EMPLOYEE",
                List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L);
    }

    private MeetingBookingRequest request(int attendees) {
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        return new MeetingBookingRequest(ROOM_ID, "产品评审", "确认发布范围", start,
                start.plusHours(1), attendees);
    }

    private MeetingRoom room(String status, int capacity) {
        MeetingRoom room = new MeetingRoom();
        room.setId(ROOM_ID);
        room.setTenantId(TENANT_ID);
        room.setCode("MR-008");
        room.setName("海棠厅");
        room.setLocation("8F");
        room.setCapacity(capacity);
        room.setStatus(status);
        room.setDeleted(false);
        return room;
    }

    private MeetingBooking booking(Long tenantId, String status, int version) {
        MeetingBooking booking = new MeetingBooking();
        booking.setId(BOOKING_ID);
        booking.setTenantId(tenantId);
        booking.setRoomId(ROOM_ID);
        booking.setOrganizerUserId(USER_ID);
        booking.setTitle("产品评审");
        booking.setStartAt(LocalDateTime.now().plusHours(1));
        booking.setEndAt(LocalDateTime.now().plusHours(2));
        booking.setAttendeeCount(5);
        booking.setStatus(status);
        booking.setVersion(version);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setUpdatedAt(LocalDateTime.now());
        return booking;
    }

    private User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setTenantId(TENANT_ID);
        user.setDisplayName("员工甲");
        user.setUsername("employee");
        return user;
    }

    private static void initializeTableMetadata(Class<?> entityClass, Class<?> mapperClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) return;
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        assistant.setCurrentNamespace(mapperClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
