package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.AttendanceToolPort;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.KnowledgeSearchItemResponse;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.dto.ApprovalFormResponse;
import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.NotificationResponse;
import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.dto.OrganizationOverviewResponse;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.PositionResponse;
import com.aiworkmate.dto.SealUsageResponse;
import com.aiworkmate.dto.VisitorBookingResponse;
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.ApprovalEngineService;
import com.aiworkmate.service.HrService;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.AttendanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDomainToolAdaptersTest {
    @Mock private LeaveWorkflowService leaveWorkflowService;
    @Mock private KnowledgeService knowledgeService;
    @Mock private NotificationService notificationService;
    @Mock private ApprovalEngineService approvalEngineService;
    @Mock private HrService hrService;
    @Mock private AdminAssetsService adminAssetsService;
    @Mock private AttendanceService attendanceService;

    private TodoAgentDomainToolAdapter todoAdapter;
    private LeaveAgentDomainToolAdapter leaveAdapter;
    private ApprovalConfigurationAgentDomainToolAdapter approvalConfigurationAdapter;
    private ApprovalTaskAgentDomainToolAdapter approvalTaskAdapter;
    private HrOrganizationAgentDomainToolAdapter organizationAdapter;
    private AttendanceAgentDomainToolAdapter attendanceAdapter;
    private VisitorAgentDomainToolAdapter visitorAdapter;
    private SealAgentDomainToolAdapter sealAdapter;
    private KnowledgeAgentDomainToolAdapter knowledgeAdapter;
    private NotificationAgentDomainToolAdapter notificationAdapter;
    private final ToolActorContext context = new ToolActorContext(91L, 7L, 10L, 20L, 1, "trace");

    @BeforeEach
    void setUp() {
        todoAdapter = new TodoAgentDomainToolAdapter(leaveWorkflowService);
        leaveAdapter = new LeaveAgentDomainToolAdapter(leaveWorkflowService);
        approvalConfigurationAdapter = new ApprovalConfigurationAgentDomainToolAdapter(approvalEngineService);
        approvalTaskAdapter = new ApprovalTaskAgentDomainToolAdapter(leaveWorkflowService);
        organizationAdapter = new HrOrganizationAgentDomainToolAdapter(hrService);
        attendanceAdapter = new AttendanceAgentDomainToolAdapter(attendanceService);
        visitorAdapter = new VisitorAgentDomainToolAdapter(adminAssetsService);
        sealAdapter = new SealAgentDomainToolAdapter(adminAssetsService);
        knowledgeAdapter = new KnowledgeAgentDomainToolAdapter(knowledgeService);
        notificationAdapter = new NotificationAgentDomainToolAdapter(notificationService);
    }

    @Test
    void forwardsTrustedTodoQueryAndMapsOnlyPortFields() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 9, 0);
        when(leaveWorkflowService.todos(7L, "PENDING", now, now.plusDays(1), 2, 30))
                .thenReturn(PageResponse.of(List.of(new TodoResponse(
                        1L, 2L, 3L, "申请人", "ANNUAL", 2, "PENDING", 4,
                        now, now.plusDays(1), false, "internal-avatar", now, "/private/avatar")),
                        1, 2, 30));

        TodoToolPort.Page result = todoAdapter.query(context,
                new TodoToolPort.Query("PENDING", now, now.plusDays(1), 2, 30));

        assertThat(result.items()).containsExactly(new TodoToolPort.Item(
                1L, 2L, "申请人", "ANNUAL", 2, "PENDING", 4,
                now, now.plusDays(1), false));
    }

    @Test
    void dispatchesApprovalResourceThroughTypedDomainMethod() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 8, 30);
        when(approvalEngineService.listForms(7L, "报销", "ENABLED", 1, 20))
                .thenReturn(PageResponse.of(List.of(new ApprovalFormResponse(
                        12L, "expense", "费用报销", "报销表单", "{sensitive-schema}",
                        "ENABLED", 3, "管理员", now.minusDays(1), now, true, true)), 1, 1, 20));

        var result = approvalConfigurationAdapter.query(context, new ApprovalConfigurationToolPort.Query(
                ApprovalConfigurationToolPort.Resource.FORM, "报销", "ENABLED", 1, 20));

        assertThat(result.items()).containsExactly(new ApprovalConfigurationToolPort.Item(
                12L, ApprovalConfigurationToolPort.Resource.FORM, "expense", "费用报销", "报销表单",
                "ENABLED", 3, null, null, null, now));
        assertThat(result.toString()).doesNotContain("sensitive-schema");
    }

    @Test
    void forwardsTenantApprovalQueryAndDropsInternalIdentityFields() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 9, 30, 23, 59);
        when(leaveWorkflowService.adminList(7L, "PENDING", from, to, "张三", "ANNUAL", 2, 30))
                .thenReturn(PageResponse.of(List.of(leaveApplication()), 1, 2, 30));

        var result = approvalTaskAdapter.query(context, new ApprovalTaskToolPort.Query(
                "PENDING", from, to, "张三", "ANNUAL", 2, 30));

        assertThat(result.items()).containsExactly(new ApprovalTaskToolPort.Item(
                30L, null, "当前用户", "直属主管", "PERSONAL", 1.0,
                "DRAFT", 0, null, null, false));
        assertThat(result.toString()).doesNotContain("applicantUserId", "approverUserId");
        verify(leaveWorkflowService).adminList(7L, "PENDING", from, to, "张三", "ANNUAL", 2, 30);
    }

    @Test
    void mapsVisibleOrganizationAndFiltersWithoutLeakingContactData() {
        when(hrService.overviewForActor(7L)).thenReturn(new OrganizationOverviewResponse(
                List.of(new DepartmentResponse(1L, "RD", "研发部", null, 9L, 1)),
                List.of(new PositionResponse(2L, "DEV", "工程师", 1)),
                List.of(new OrganizationOverviewResponse.EmployeeSummary(
                        3L, "张三", "secret@example.com", "EMPLOYEE", 1,
                        1L, 2L, 9L, "李经理", "/avatar/private", "/avatar/manager"))));

        var result = organizationAdapter.query(context,
                new com.aiworkmate.agent.tool.port.HrOrganizationToolPort.Query("研发", 20));

        assertThat(result.departments()).hasSize(1);
        assertThat(result.positions()).isEmpty();
        assertThat(result.employees()).isEmpty();
        assertThat(result.toString()).doesNotContain("secret@example.com", "avatar", "9L");
        verify(hrService).overviewForActor(7L);
    }

    @Test
    void dispatchesAttendanceResourceAndDropsIpAndInternalUserIds() {
        LocalDate day = LocalDate.of(2026, 9, 14);
        when(attendanceService.getTodayStatus(7L)).thenReturn(
                new com.aiworkmate.dto.AttendanceTodayStatusResponse(
                        8L, day, day.atTime(9, 0), null, "NORMAL", 0, 0,
                        "10.0.0.1", null, false, true));

        var result = attendanceAdapter.query(context, new AttendanceToolPort.Query(
                AttendanceToolPort.Resource.TODAY, null, null, null,
                null, null, null, 1, 20));

        assertThat(result.today().status()).isEqualTo("NORMAL");
        assertThat(result.toString()).doesNotContain("10.0.0.1", "tenantId", "userId");
        verify(attendanceService).getTodayStatus(7L);
    }

    @Test
    void mapsAttendanceReissueCommandToIdempotentDomainWrite() {
        LocalDate date = LocalDate.of(2026, 9, 14);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 15, 9, 0);
        when(attendanceService.submitAgentReissue(eq(7L), org.mockito.ArgumentMatchers.any(),
                eq("operation-1"))).thenReturn(new com.aiworkmate.dto.AttendanceReissueResponse(
                31L, 7L, "当前用户", 8L, "直属主管", date, "CLOCK_IN", "忘记打卡",
                "PENDING", null, submittedAt, null, submittedAt, submittedAt,
                false, true));

        var result = attendanceAdapter.submitReissue(context,
                new AttendanceToolPort.ReissueCommand(date, "CLOCK_IN", "忘记打卡"),
                new ToolOperationKey("operation-1"));

        assertThat(result).isEqualTo(new AttendanceToolPort.ReissueWriteResult(
                31L, "PENDING", date, "CLOCK_IN", submittedAt));
        var request = ArgumentCaptor.forClass(com.aiworkmate.dto.AttendanceReissueRequest.class);
        verify(attendanceService).submitAgentReissue(eq(7L), request.capture(), eq("operation-1"));
        assertThat(request.getValue()).isEqualTo(
                new com.aiworkmate.dto.AttendanceReissueRequest(date, "CLOCK_IN", "忘记打卡"));
    }

    @Test
    void mapsVisitorSummaryWithoutPhonePlateOrInternalIdentities() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 9, 30);
        when(adminAssetsService.listMyVisitorBookings(7L, "APPROVED", 1, 20))
                .thenReturn(PageResponse.of(List.of(new VisitorBookingResponse(
                        31L, 7L, "申请人", 8L, "审批人", 9L, "接待人",
                        "访客", "合作公司", "13800000000", "项目交流", now.plusDays(1),
                        now.plusDays(1).plusHours(2), "粤A00000", 2, "APPROVED", 3,
                        101L, 102L, 4, "APPROVED", now, now.plusHours(1), 10L,
                        "登记人", null, null, null, null, now.minusDays(1), now,
                        false, false, true, false, false, false)), 1, 1, 20));

        var result = visitorAdapter.query(context,
                new com.aiworkmate.agent.tool.port.VisitorToolPort.Query(
                        null, com.aiworkmate.agent.tool.port.VisitorToolPort.Queue.MINE,
                        "APPROVED", 1, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).visitorName()).isEqualTo("访客");
        assertThat(result.toString()).doesNotContain(
                "13800000000", "粤A00000", "applicantUserId", "hostUserId",
                "workflowInstanceId", "taskId");
    }

    @Test
    void mapsVisitorApplicationAndReadOnlyVerificationToTypedDomainContracts() {
        LocalDateTime visitAt = LocalDateTime.of(2026, 9, 20, 9, 0);
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 17, 16, 0);
        var command = new com.aiworkmate.agent.tool.port.VisitorToolPort.ApplicationCommand(
                "访客甲", "合作公司", "13800000000", "项目交流", 9,
                visitAt, visitAt.plusHours(2), "粤A00000", 2);
        var domain = new com.aiworkmate.service.model.VisitorAgentApplicationCommand(
                "访客甲", "合作公司", "13800000000", "项目交流", 9,
                visitAt, visitAt.plusHours(2), "粤A00000", 2);
        var receipt = new com.aiworkmate.service.model.VisitorAgentApplicationReceipt(
                31, "PENDING", 0, submittedAt);
        when(adminAssetsService.submitVisitorBookingAgent(7L, domain, "operation-1"))
                .thenReturn(receipt);
        when(adminAssetsService.findAgentVisitorBooking(7L, domain, "operation-1"))
                .thenReturn(java.util.Optional.of(receipt));

        var expected = new com.aiworkmate.agent.tool.port.VisitorToolPort.ApplicationResult(
                31, "PENDING", 0, submittedAt);
        assertThat(visitorAdapter.apply(context, command, new ToolOperationKey("operation-1")))
                .isEqualTo(expected);
        assertThat(visitorAdapter.findApplication(context, command, new ToolOperationKey("operation-1")))
                .isEqualTo(com.aiworkmate.agent.tool.port.ToolWriteVerification.observed(expected));
        verify(adminAssetsService).submitVisitorBookingAgent(7L, domain, "operation-1");
        verify(adminAssetsService).findAgentVisitorBooking(7L, domain, "operation-1");
    }

    @Test
    void mapsVisitorCheckInAndReadOnlyVerificationToTypedDomainContracts() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 20, 8, 55);
        var command = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitCommand(
                31, 2, "已核验证件");
        var domain = new com.aiworkmate.service.model.VisitorAgentVisitCommand(
                31, 2, "已核验证件");
        var receipt = new com.aiworkmate.service.model.VisitorAgentVisitReceipt(
                31, "CHECKED_IN", 3, occurredAt);
        when(adminAssetsService.checkInVisitorAgent(7L, domain, "operation-2"))
                .thenReturn(receipt);
        when(adminAssetsService.findAgentVisitorCheckIn(7L, domain, "operation-2"))
                .thenReturn(java.util.Optional.of(receipt));

        var expected = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitResult(
                31, "CHECKED_IN", 3, occurredAt);
        assertThat(visitorAdapter.checkIn(context, command, new ToolOperationKey("operation-2")))
                .isEqualTo(expected);
        assertThat(visitorAdapter.findCheckIn(context, command, new ToolOperationKey("operation-2")))
                .isEqualTo(com.aiworkmate.agent.tool.port.ToolWriteVerification.observed(expected));
        verify(adminAssetsService).checkInVisitorAgent(7L, domain, "operation-2");
        verify(adminAssetsService).findAgentVisitorCheckIn(7L, domain, "operation-2");
    }

    @Test
    void mapsVisitorArrivalAndReadOnlyVerificationToTheSharedVisitContract() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 20, 9, 0);
        var command = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitCommand(
                31, 3, "前台确认到访");
        var domain = new com.aiworkmate.service.model.VisitorAgentVisitCommand(
                31, 3, "前台确认到访");
        var receipt = new com.aiworkmate.service.model.VisitorAgentVisitReceipt(
                31, "VISITED", 4, occurredAt);
        when(adminAssetsService.markVisitorArrivedAgent(7L, domain, "operation-3"))
                .thenReturn(receipt);
        when(adminAssetsService.findAgentVisitorArrival(7L, domain, "operation-3"))
                .thenReturn(java.util.Optional.of(receipt));

        var expected = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitResult(
                31, "VISITED", 4, occurredAt);
        assertThat(visitorAdapter.markArrived(context, command, new ToolOperationKey("operation-3")))
                .isEqualTo(expected);
        assertThat(visitorAdapter.findArrival(context, command, new ToolOperationKey("operation-3")))
                .isEqualTo(com.aiworkmate.agent.tool.port.ToolWriteVerification.observed(expected));
        verify(adminAssetsService).markVisitorArrivedAgent(7L, domain, "operation-3");
        verify(adminAssetsService).findAgentVisitorArrival(7L, domain, "operation-3");
    }

    @Test
    void mapsSealSummaryWithoutWorkflowOrStorageMetadata() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 14, 10, 0);
        when(adminAssetsService.getSealUsage(7L, 41L)).thenReturn(new SealUsageResponse(
                41L, 7L, "申请人", 8L, "审批人", "公章", "采购合同", "签约", 2,
                "APPROVED", 5, 201L, 202L, 6, "APPROVED", now.minusHours(2), now.minusHours(1),
                null, null, null, null, null, now.minusDays(1), now,
                false, false, true, false, false));

        var result = sealAdapter.query(context,
                new com.aiworkmate.agent.tool.port.SealToolPort.Query(
                        41L, com.aiworkmate.agent.tool.port.SealToolPort.Queue.MINE, null, 1, 20));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).documentTitle()).isEqualTo("采购合同");
        assertThat(result.toString()).doesNotContain(
                "workflowInstanceId", "taskId", "storage", "objectKey");
    }

    @Test
    void keepsLeaveOperationKeyAndMapsWriteResult() {
        when(leaveWorkflowService.createAgentDraft(eq(7L), org.mockito.ArgumentMatchers.any(), eq("operation-1")))
                .thenReturn(leaveApplication());
        LeaveToolPort.Draft command = new LeaveToolPort.Draft(
                "PERSONAL", 8L, LocalDate.of(2026, 9, 15), "AM",
                LocalDate.of(2026, 9, 15), "PM", "家庭事务");

        LeaveToolPort.WriteResult result = leaveAdapter.createDraft(
                context, command, new ToolOperationKey("operation-1"));

        assertThat(result).isEqualTo(new LeaveToolPort.WriteResult(30L, "DRAFT", 0, null));
        var request = ArgumentCaptor.forClass(com.aiworkmate.dto.LeaveApplicationRequest.class);
        verify(leaveWorkflowService).createAgentDraft(eq(7L), request.capture(), eq("operation-1"));
        assertThat(request.getValue().reason()).isEqualTo("家庭事务");
        assertThat(request.getValue().version()).isNull();
    }

    @Test
    void mapsKnowledgeSearchWithoutExposingProviderMetadata() {
        when(knowledgeService.search(eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new KnowledgeSearchResponse("provider", "model", 8, List.of(
                        new KnowledgeSearchItemResponse(11L, 12L, "policy.txt", 3,
                                "制度内容", 0.86, "HYBRID"))));

        KnowledgeToolPort.Result result = knowledgeAdapter.search(context,
                new KnowledgeToolPort.Query("请假制度", 5, 0.5));

        assertThat(result.items()).containsExactly(new KnowledgeToolPort.Item(
                "制度内容", 0.86, "HYBRID", 11L, 12L, "policy.txt", 3));
        verify(knowledgeService).search(eq(7L), org.mockito.ArgumentMatchers.argThat(request ->
                request.query().equals("请假制度") && request.topK() == 5 && request.minScore() == 0.5));
    }

    @Test
    void mapsSelfOwnedNotificationsAndDropsInternalBusinessId() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        when(notificationService.list(7L, 1, 20)).thenReturn(PageResponse.of(List.of(
                new NotificationResponse(9L, "approval", "审批提醒", "请处理", "leave", 999L, false, now)),
                1, 1, 20));

        var result = notificationAdapter.mine(context, 1, 20);

        assertThat(result.items()).containsExactly(new com.aiworkmate.agent.tool.port.NotificationToolPort.Item(
                9L, "approval", "审批提醒", "请处理", "leave", false, now));
    }

    @Test
    void marksOnlyTheTrustedUsersNotificationAsRead() {
        var result = notificationAdapter.markRead(context, 9L);

        assertThat(result).isEqualTo(new com.aiworkmate.agent.tool.port.NotificationToolPort.ReadResult(9L, true));
        verify(notificationService).markRead(7L, 9L);
    }

    private LeaveApplicationResponse leaveApplication() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 11, 0);
        return new LeaveApplicationResponse(
                30L, 7L, "当前用户", 8L, "直属主管", "PERSONAL",
                LocalDate.of(2026, 9, 15), "AM", LocalDate.of(2026, 9, 15), "PM",
                2, 1.0, "家庭事务", "DRAFT", 0,
                null, null, null, null, false, 0, null, null, false,
                null, "DRAFT", List.of(), null, null, now, now,
                true, true, false, false, null, null);
    }
}
