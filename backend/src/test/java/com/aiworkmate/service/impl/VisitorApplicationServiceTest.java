package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.entity.User;
import com.aiworkmate.entity.VisitorBooking;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
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
import com.aiworkmate.service.model.VisitorAgentApplicationCommand;
import com.aiworkmate.service.model.VisitorAgentApplicationReceipt;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitorApplicationServiceTest {
    private static final long TENANT_ID = 1L;
    private static final long ACTOR_ID = 1001L;
    private static final long APPROVER_ID = 2002L;
    private static final long HOST_ID = 3003L;

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
    private LocalDateTime visitAt;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(VisitorBooking.class, VisitorBookingMapper.class);
        service = new AdminAssetsServiceImpl(assetMapper, operationMapper, accessControlMapper,
                meetingRoomMapper, meetingBookingMapper, visitorMapper, sealMapper, userMapper, instanceMapper,
                taskMapper, actionLogMapper, userAccessService, auditService, notificationService);
        visitAt = LocalDateTime.of(2026, 9, 20, 9, 0);
    }

    @Test
    void agentApplicationUsesTrustedApplicantAndCreatesOneApprovalFlow() {
        stubAccess();
        User applicant = user(ACTOR_ID, TENANT_ID);
        applicant.setApproverUserId(APPROVER_ID);
        when(userMapper.lockActiveApplicant(TENANT_ID, ACTOR_ID)).thenReturn(applicant);
        when(visitorMapper.findAgentOperation(TENANT_ID, ACTOR_ID, "operation")).thenReturn(null);
        when(userMapper.selectById(HOST_ID)).thenReturn(user(HOST_ID, TENANT_ID));
        when(userMapper.selectById(ACTOR_ID)).thenReturn(applicant);
        when(userMapper.selectById(APPROVER_ID)).thenReturn(user(APPROVER_ID, TENANT_ID));
        when(visitorMapper.selectDefinitionId(TENANT_ID)).thenReturn(11L);
        when(visitorMapper.insert(any(VisitorBooking.class))).thenAnswer(invocation -> {
            invocation.<VisitorBooking>getArgument(0).setId(31L);
            return 1;
        });
        when(instanceMapper.insert(any(WorkflowInstance.class))).thenAnswer(invocation -> {
            invocation.<WorkflowInstance>getArgument(0).setId(41L);
            return 1;
        });
        when(taskMapper.insert(any(WorkflowTask.class))).thenAnswer(invocation -> {
            invocation.<WorkflowTask>getArgument(0).setId(51L);
            return 1;
        });
        when(visitorMapper.update(any(), any())).thenReturn(1);

        VisitorAgentApplicationReceipt result = service.submitVisitorBookingAgent(
                ACTOR_ID, command(), "operation");

        assertThat(result.bookingId()).isEqualTo(31);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.version()).isZero();
        ArgumentCaptor<VisitorBooking> booking = ArgumentCaptor.forClass(VisitorBooking.class);
        verify(visitorMapper).insert(booking.capture());
        assertThat(booking.getValue().getApplicantUserId()).isEqualTo(ACTOR_ID);
        assertThat(booking.getValue().getApproverUserId()).isEqualTo(APPROVER_ID);
        assertThat(booking.getValue().getAgentOperationKey()).isEqualTo("operation");
        verify(auditService).recordTransactional(TENANT_ID, ACTOR_ID, "VISITOR_BOOKING",
                "31", "SUBMIT", "SUCCESS", "Agent 提交访客预约");
    }

    @Test
    void replayReturnsCurrentReceiptAndRejectsChangedCommand() {
        stubAccess();
        User applicant = user(ACTOR_ID, TENANT_ID);
        when(userMapper.lockActiveApplicant(TENANT_ID, ACTOR_ID)).thenReturn(applicant);
        VisitorBooking existing = booking(command());
        existing.setStatus("APPROVED");
        existing.setVersion(1);
        when(visitorMapper.findAgentOperation(TENANT_ID, ACTOR_ID, "operation")).thenReturn(existing);

        assertThat(service.submitVisitorBookingAgent(ACTOR_ID, command(), "operation"))
                .isEqualTo(new VisitorAgentApplicationReceipt(
                        31, "APPROVED", 1, existing.getSubmittedAt()));
        assertThatThrownBy(() -> service.submitVisitorBookingAgent(ACTOR_ID,
                new VisitorAgentApplicationCommand("另一位访客", "合作公司", "13800000000", "项目交流",
                        HOST_ID, visitAt, visitAt.plusHours(2), "粤A00000", 2), "operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_CONFLICT");
        verify(visitorMapper, never()).insert(any(VisitorBooking.class));
        verifyNoInteractions(auditService, notificationService);
    }

    @Test
    void rejectsHostOutsideAuthenticatedTenant() {
        stubAccess();
        User applicant = user(ACTOR_ID, TENANT_ID);
        applicant.setApproverUserId(APPROVER_ID);
        when(userMapper.lockActiveApplicant(TENANT_ID, ACTOR_ID)).thenReturn(applicant);
        when(visitorMapper.findAgentOperation(TENANT_ID, ACTOR_ID, "operation")).thenReturn(null);
        when(userMapper.selectById(HOST_ID)).thenReturn(user(HOST_ID, TENANT_ID + 1));

        assertThatThrownBy(() -> service.submitVisitorBookingAgent(ACTOR_ID, command(), "operation"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
        verify(visitorMapper, never()).insert(any(VisitorBooking.class));
    }

    private VisitorAgentApplicationCommand command() {
        return new VisitorAgentApplicationCommand(
                "访客甲", "合作公司", "13800000000", "项目交流", HOST_ID,
                visitAt, visitAt.plusHours(2), "粤A00000", 2);
    }

    private VisitorBooking booking(VisitorAgentApplicationCommand command) {
        VisitorBooking booking = new VisitorBooking();
        booking.setId(31L);
        booking.setTenantId(TENANT_ID);
        booking.setApplicantUserId(ACTOR_ID);
        booking.setAgentOperationKey("operation");
        booking.setVisitorName(command.visitorName());
        booking.setVisitorCompany(command.visitorCompany());
        booking.setVisitorPhone(command.visitorPhone());
        booking.setPurpose(command.purpose());
        booking.setHostUserId(command.hostUserId());
        booking.setExpectedVisitAt(command.expectedVisitAt());
        booking.setExpectedLeaveAt(command.expectedLeaveAt());
        booking.setPlateNumber(command.plateNumber());
        booking.setPartySize(command.partySize());
        booking.setSubmittedAt(LocalDateTime.of(2026, 9, 17, 16, 0));
        return booking;
    }

    private void stubAccess() {
        when(userAccessService.resolveActiveUser(ACTOR_ID)).thenReturn(new ResolvedUserAccess(
                ACTOR_ID, "applicant@example.com", TENANT_ID, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("visitor:create"), List.of("SELF"), 1L));
    }

    private User user(long id, long tenantId) {
        User user = new User();
        user.setId(id);
        user.setTenantId(tenantId);
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
