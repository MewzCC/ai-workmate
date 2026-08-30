package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.VisitorBookingResponse;
import com.aiworkmate.dto.VisitorVisitActionRequest;
import com.aiworkmate.entity.User;
import com.aiworkmate.entity.VisitorBooking;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AssetLedgerMapper;
import com.aiworkmate.mapper.AssetOperationMapper;
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
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorVisitLifecycleServiceTest {
    private static final long TENANT_ID = 1L;
    private static final long ACTOR_ID = 1001L;
    private static final long VISITOR_ID = 88L;

    @Mock private AssetLedgerMapper assetMapper;
    @Mock private AssetOperationMapper operationMapper;
    @Mock private AccessControlMapper accessControlMapper;
    @Mock private MeetingRoomMapper meetingRoomMapper;
    @Mock private MeetingBookingMapper meetingBookingMapper;
    @Mock private VisitorBookingMapper visitorMapper;
    @Mock private SealUsageMapper sealMapper;
    @Mock private UserMapper userMapper;
    @Mock private WorkflowInstanceMapper instanceMapper;
    @Mock private WorkflowTaskMapper taskMapper;
    @Mock private WorkflowActionLogMapper actionLogMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;
    @Mock private NotificationService notificationService;

    private AdminAssetsServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(VisitorBooking.class, VisitorBookingMapper.class);
        service = new AdminAssetsServiceImpl(assetMapper, operationMapper, accessControlMapper,
                meetingRoomMapper, meetingBookingMapper, visitorMapper, sealMapper, userMapper, instanceMapper,
                taskMapper, actionLogMapper, userAccessService, auditService, notificationService);
    }

    @Test
    void checkInMovesApprovedBookingAndWritesAudit() {
        stubAccess(access(ACTOR_ID, List.of("visitor:register")));
        VisitorBooking before = booking("APPROVED", 2, ACTOR_ID, ACTOR_ID);
        VisitorBooking after = booking("CHECKED_IN", 3, ACTOR_ID, ACTOR_ID);
        after.setRegisteredByUserId(ACTOR_ID);
        after.setCheckedInAt(LocalDateTime.now());
        when(visitorMapper.selectById(VISITOR_ID)).thenReturn(before, after);
        when(visitorMapper.update(any(), any())).thenReturn(1);
        when(userMapper.selectById(anyLong())).thenReturn(user(ACTOR_ID));

        VisitorBookingResponse response = service.checkInVisitor(ACTOR_ID, VISITOR_ID,
                new VisitorVisitActionRequest(2, "前台核验证件"));

        assertThat(response.status()).isEqualTo("CHECKED_IN");
        assertThat(response.checkedInAt()).isNotNull();
        assertThat(response.registeredByUserId()).isEqualTo(ACTOR_ID);
        assertThat(response.canMarkVisited()).isTrue();
        verify(auditService).recordTransactional(TENANT_ID, ACTOR_ID, "VISITOR_BOOKING",
                Long.toString(VISITOR_ID), "CHECK_IN", "SUCCESS", "fromStatus=APPROVED,toStatus=CHECKED_IN,remark=前台核验证件");
    }

    @Test
    void arrivalRequiresCheckedInStatus() {
        stubAccess(access(ACTOR_ID, List.of("visitor:register")));
        when(visitorMapper.selectById(VISITOR_ID)).thenReturn(booking("APPROVED", 2, ACTOR_ID, ACTOR_ID));

        assertThatThrownBy(() -> service.markVisitorArrived(ACTOR_ID, VISITOR_ID,
                new VisitorVisitActionRequest(2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        verify(visitorMapper, never()).update(any(), any());
        verify(auditService, never()).recordTransactional(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    @Test
    void leaveMovesArrivedBookingToTerminalState() {
        stubAccess(access(ACTOR_ID, List.of("visitor:register")));
        VisitorBooking before = booking("VISITED", 4, ACTOR_ID, ACTOR_ID);
        VisitorBooking after = booking("LEFT", 5, ACTOR_ID, ACTOR_ID);
        after.setRegisteredByUserId(ACTOR_ID);
        after.setLeftAt(LocalDateTime.now());
        when(visitorMapper.selectById(VISITOR_ID)).thenReturn(before, after);
        when(visitorMapper.update(any(), any())).thenReturn(1);
        when(userMapper.selectById(anyLong())).thenReturn(user(ACTOR_ID));

        VisitorBookingResponse response = service.leaveVisitor(ACTOR_ID, VISITOR_ID,
                new VisitorVisitActionRequest(4, "前台确认离场"));

        assertThat(response.status()).isEqualTo("LEFT");
        assertThat(response.leftAt()).isNotNull();
        assertThat(response.canLeave()).isFalse();
        verify(auditService).recordTransactional(TENANT_ID, ACTOR_ID, "VISITOR_BOOKING",
                Long.toString(VISITOR_ID), "LEAVE", "SUCCESS",
                "fromStatus=VISITED,toStatus=LEFT,remark=前台确认离场");
    }

    @Test
    void noShowCannotBeMarkedBeforeExpectedVisitTime() {
        stubAccess(access(ACTOR_ID, List.of("visitor:register")));
        VisitorBooking booking = booking("APPROVED", 2, ACTOR_ID, ACTOR_ID);
        booking.setExpectedVisitAt(LocalDateTime.now().plusHours(1));
        when(visitorMapper.selectById(VISITOR_ID)).thenReturn(booking);

        assertThatThrownBy(() -> service.markVisitorNoShow(ACTOR_ID, VISITOR_ID,
                new VisitorVisitActionRequest(2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");

        verify(visitorMapper, never()).update(any(), any());
    }

    @Test
    void unrelatedUserCannotRegisterVisitWithoutGlobalPermission() {
        long unrelatedUserId = 3003L;
        stubAccess(access(unrelatedUserId, List.of("visitor:register")));
        when(visitorMapper.selectById(VISITOR_ID)).thenReturn(booking("APPROVED", 2, ACTOR_ID, ACTOR_ID));

        assertThatThrownBy(() -> service.checkInVisitor(unrelatedUserId, VISITOR_ID,
                new VisitorVisitActionRequest(2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_FORBIDDEN");

        verify(visitorMapper, never()).update(any(), any());
    }

    @Test
    void optimisticConflictDoesNotWriteVisitAudit() {
        stubAccess(access(ACTOR_ID, List.of("visitor:register")));
        when(visitorMapper.selectById(VISITOR_ID)).thenReturn(booking("APPROVED", 2, ACTOR_ID, ACTOR_ID));
        when(visitorMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.checkInVisitor(ACTOR_ID, VISITOR_ID,
                new VisitorVisitActionRequest(1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");

        verify(auditService, never()).recordTransactional(anyLong(), anyLong(), anyString(), anyString(),
                anyString(), anyString(), anyString());
    }

    private void stubAccess(ResolvedUserAccess access) {
        when(userAccessService.resolveActiveUser(access.userId())).thenReturn(access);
    }

    private ResolvedUserAccess access(Long userId, List<String> permissions) {
        return new ResolvedUserAccess(userId, "visitor@example.com", TENANT_ID, "EMPLOYEE",
                List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L);
    }

    private VisitorBooking booking(String status, int version, Long applicantId, Long hostId) {
        VisitorBooking booking = new VisitorBooking();
        booking.setId(VISITOR_ID);
        booking.setTenantId(TENANT_ID);
        booking.setApplicantUserId(applicantId);
        booking.setApproverUserId(2002L);
        booking.setHostUserId(hostId);
        booking.setVisitorName("访客甲");
        booking.setPurpose("商务洽谈");
        booking.setExpectedVisitAt(LocalDateTime.now().minusHours(1));
        booking.setStatus(status);
        booking.setVersion(version);
        booking.setSubmittedAt(LocalDateTime.now().minusDays(1));
        booking.setCreatedAt(LocalDateTime.now().minusDays(1));
        booking.setUpdatedAt(LocalDateTime.now());
        return booking;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setTenantId(TENANT_ID);
        user.setDisplayName("前台员工");
        user.setUsername("visitor-operator");
        user.setStatus(1);
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
