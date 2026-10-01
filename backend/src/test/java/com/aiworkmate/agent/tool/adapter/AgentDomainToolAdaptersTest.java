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
import com.aiworkmate.dto.ApprovalProcessResponse;
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
import com.aiworkmate.service.KnowledgeBaseService;
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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentDomainToolAdaptersTest {
    @Mock private LeaveWorkflowService leaveWorkflowService;
    @Mock private KnowledgeService knowledgeService;
    @Mock private KnowledgeBaseService knowledgeBaseService;
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
        knowledgeAdapter = new KnowledgeAgentDomainToolAdapter(knowledgeService, knowledgeBaseService);
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
    void defaultsUnfilteredTodoQueryToPendingLikeMyTodoPage() {
        when(leaveWorkflowService.todos(7L, "PENDING", null, null, 1, 20))
                .thenReturn(PageResponse.of(List.of(), 0, 1, 20));
        TodoToolPort.Page result = todoAdapter.query(context,
                new TodoToolPort.Query(null, null, null, 1, 20));
        assertThat(result.total()).isZero();
        verify(leaveWorkflowService).todos(7L, "PENDING", null, null, 1, 20);
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
    void createsOnlyDisabledApprovalFormDraftFromSemanticFields() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 22, 10);
        var command = new ApprovalConfigurationToolPort.FormDraft(
                "travel", "出差申请", "员工出差申请", List.of(
                new ApprovalConfigurationToolPort.FormField(
                        "reason", "出差事由", "textarea", true, "请输入", List.of(), "full")));
        when(approvalEngineService.createFormDraftAgent(eq(7L), any())).thenReturn(new ApprovalFormResponse(
                31L, "travel", "出差申请", "员工出差申请", "{hidden}",
                "DISABLED", 1, "管理员", now, now, true, true));

        var result = approvalConfigurationAdapter.createFormDraft(context, command);

        assertThat(result).isEqualTo(new ApprovalConfigurationToolPort.FormDraftResult(
                31L, "travel", "DISABLED", 1, now));
        var request = org.mockito.ArgumentCaptor.forClass(com.aiworkmate.dto.ApprovalFormAgentDraftRequest.class);
        verify(approvalEngineService).createFormDraftAgent(eq(7L), request.capture());
        assertThat(request.getValue().formKey()).isEqualTo("travel");
        assertThat(request.getValue().fields()).extracting(com.aiworkmate.dto.ApprovalFormAgentDraftRequest.Field::name)
                .containsExactly("reason");
    }

    @Test
    void updatesOnlyVersionBoundDisabledApprovalFormDraft() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 22, 30);
        var command = new ApprovalConfigurationToolPort.FormDraftUpdate(
                31L, 2, "出差申请（新版）", "更新说明", List.of(
                new ApprovalConfigurationToolPort.FormField(
                        "reason", "出差事由", "textarea", true, "请输入", List.of(), "full")));
        when(approvalEngineService.updateFormDraftAgent(eq(7L), eq(31L), any())).thenReturn(
                new ApprovalFormResponse(31L, "travel", "出差申请（新版）", "更新说明", "{hidden}",
                        "DISABLED", 3, "管理员", now.minusDays(1), now, true, true));

        var result = approvalConfigurationAdapter.updateFormDraft(context, command);

        assertThat(result).isEqualTo(new ApprovalConfigurationToolPort.FormDraftResult(
                31L, "travel", "DISABLED", 3, now));
        var request = org.mockito.ArgumentCaptor.forClass(
                com.aiworkmate.dto.ApprovalFormAgentDraftUpdateRequest.class);
        verify(approvalEngineService).updateFormDraftAgent(eq(7L), eq(31L), request.capture());
        assertThat(request.getValue().version()).isEqualTo(2);
        assertThat(request.getValue().fields())
                .extracting(com.aiworkmate.dto.ApprovalFormAgentDraftUpdateRequest.Field::name)
                .containsExactly("reason");
    }

    @Test
    void publishesOnlyTheVersionBoundApprovalFormDraft() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 0, 10);
        var command = new ApprovalConfigurationToolPort.VersionedForm(31L, 2);
        when(approvalEngineService.publishFormDraftAgent(7L, 31L, 2)).thenReturn(
                new ApprovalFormResponse(31L, "travel", "出差申请", null, "{hidden}",
                        "ENABLED", 3, "管理员", now.minusDays(1), now, true, true));

        var result = approvalConfigurationAdapter.publishFormDraft(context, command);

        assertThat(result).isEqualTo(new ApprovalConfigurationToolPort.FormDraftResult(
                31L, "travel", "ENABLED", 3, now));
        verify(approvalEngineService).publishFormDraftAgent(7L, 31L, 2);
    }

    @Test
    void createsOnlyDisabledApprovalProcessDraftFromSemanticNodes() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 22, 50);
        var command = new ApprovalConfigurationToolPort.ProcessDraft(
                "travel", "出差审批", null, 31L, List.of(
                new ApprovalConfigurationToolPort.ProcessNode("START", "开始", null, null, null, null, null, null),
                new ApprovalConfigurationToolPort.ProcessNode("APPROVAL", "主管审批", "DIRECT_MANAGER", "", "OR_SIGN", false, 48, "REMIND"),
                new ApprovalConfigurationToolPort.ProcessNode("END", "结束", null, null, null, null, null, null)));
        when(approvalEngineService.createProcessDraftAgent(eq(7L), any())).thenReturn(
                new ApprovalProcessResponse(41L, "travel", "出差审批", null, 31L, "出差申请",
                        "[{hidden}]", "DISABLED", 1, "管理员", now, now, true, true));

        var result = approvalConfigurationAdapter.createProcessDraft(context, command);

        assertThat(result.status()).isEqualTo("DISABLED");
        var request = org.mockito.ArgumentCaptor.forClass(
                com.aiworkmate.dto.ApprovalProcessAgentDraftRequest.class);
        verify(approvalEngineService).createProcessDraftAgent(eq(7L), request.capture());
        assertThat(request.getValue().nodes()).hasSize(3);
    }

    @Test
    void updatesOnlyVersionBoundDisabledApprovalProcessDraft() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 23, 10);
        var command = new ApprovalConfigurationToolPort.ProcessDraftUpdate(
                41L, 2, "出差审批（新版）", "更新说明", 31L, List.of(
                new ApprovalConfigurationToolPort.ProcessNode("START", "开始", null, null, null, null, null, null),
                new ApprovalConfigurationToolPort.ProcessNode("APPROVAL", "主管审批", "DIRECT_MANAGER", "", "OR_SIGN", false, 48, "REMIND"),
                new ApprovalConfigurationToolPort.ProcessNode("END", "结束", null, null, null, null, null, null)));
        when(approvalEngineService.updateProcessDraftAgent(eq(7L), eq(41L), any())).thenReturn(
                new ApprovalProcessResponse(41L, "travel", "出差审批（新版）", "更新说明", 31L, "出差申请",
                        "[{hidden}]", "DISABLED", 3, "管理员", now.minusDays(1), now, true, true));

        var result = approvalConfigurationAdapter.updateProcessDraft(context, command);

        assertThat(result.version()).isEqualTo(3);
        var request = org.mockito.ArgumentCaptor.forClass(
                com.aiworkmate.dto.ApprovalProcessAgentDraftUpdateRequest.class);
        verify(approvalEngineService).updateProcessDraftAgent(eq(7L), eq(41L), request.capture());
        assertThat(request.getValue().version()).isEqualTo(2);
        assertThat(request.getValue().nodes()).hasSize(3);
    }

    @Test
    void publishesOnlyTheVersionBoundApprovalProcessDraft() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 0, 30);
        var command = new ApprovalConfigurationToolPort.VersionedProcess(41L, 2);
        when(approvalEngineService.publishProcessDraftAgent(7L, 41L, 2)).thenReturn(
                new ApprovalProcessResponse(41L, "travel", "出差审批", null, 31L, "出差申请",
                        "[{hidden}]", "ENABLED", 3, "管理员", now.minusDays(1), now, true, true));

        var result = approvalConfigurationAdapter.publishProcessDraft(context, command);

        assertThat(result).isEqualTo(new ApprovalConfigurationToolPort.ProcessDraftResult(
                41L, "travel", "ENABLED", 3, now));
        verify(approvalEngineService).publishProcessDraftAgent(7L, 41L, 2);
    }

    @Test
    void createsOnlyDisabledApprovalRuleDraftFromSemanticConditions() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 23, 30);
        var command = new ApprovalConfigurationToolPort.RuleDraft(
                "large-expense", "大额费用复核", "AMOUNT_THRESHOLD", 10, null, "AND",
                List.of(new ApprovalConfigurationToolPort.RuleCondition("amount", "gte", "5000")),
                new ApprovalConfigurationToolPort.RuleAction("FINANCE_REVIEW", true, "OR_SIGN"));
        when(approvalEngineService.createRuleDraftAgent(eq(7L), any())).thenReturn(
                new com.aiworkmate.dto.ApprovalRuleResponse(51L, "large-expense", "大额费用复核",
                        "AMOUNT_THRESHOLD", 10, "{hidden}", "{hidden}", null, "DISABLED", 1,
                        "管理员", now, now, true, true));

        var result = approvalConfigurationAdapter.createRuleDraft(context, command);

        assertThat(result.status()).isEqualTo("DISABLED");
        var request = org.mockito.ArgumentCaptor.forClass(com.aiworkmate.dto.ApprovalRuleAgentDraftRequest.class);
        verify(approvalEngineService).createRuleDraftAgent(eq(7L), request.capture());
        assertThat(request.getValue().conditions()).hasSize(1);
    }

    @Test
    void updatesOnlyVersionBoundDisabledApprovalRuleDraft() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 23, 50);
        var command = new ApprovalConfigurationToolPort.RuleDraftUpdate(
                51L, 2, "大额费用复核（新版）", "AMOUNT_THRESHOLD", 5, null, "AND",
                List.of(new ApprovalConfigurationToolPort.RuleCondition("amount", "gte", "8000")),
                new ApprovalConfigurationToolPort.RuleAction("FINANCE_REVIEW", true, "OR_SIGN"));
        when(approvalEngineService.updateRuleDraftAgent(eq(7L), eq(51L), any())).thenReturn(
                new com.aiworkmate.dto.ApprovalRuleResponse(51L, "large-expense", "大额费用复核（新版）",
                        "AMOUNT_THRESHOLD", 5, "{hidden}", "{hidden}", null, "DISABLED", 3,
                        "管理员", now.minusDays(1), now, true, true));

        var result = approvalConfigurationAdapter.updateRuleDraft(context, command);

        assertThat(result.version()).isEqualTo(3);
        var request = org.mockito.ArgumentCaptor.forClass(
                com.aiworkmate.dto.ApprovalRuleAgentDraftUpdateRequest.class);
        verify(approvalEngineService).updateRuleDraftAgent(eq(7L), eq(51L), request.capture());
        assertThat(request.getValue().version()).isEqualTo(2);
        assertThat(request.getValue().conditions()).hasSize(1);
    }

    @Test
    void enablesOnlyTheVersionBoundApprovalRuleDraft() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 2, 0, 50);
        var command = new ApprovalConfigurationToolPort.VersionedRule(51L, 2);
        when(approvalEngineService.enableRuleDraftAgent(7L, 51L, 2)).thenReturn(
                new com.aiworkmate.dto.ApprovalRuleResponse(51L, "large-expense", "大额费用复核",
                        "AMOUNT_THRESHOLD", 5, "{hidden}", "{hidden}", null, "ENABLED", 3,
                        "管理员", now.minusDays(1), now, true, true));

        var result = approvalConfigurationAdapter.enableRuleDraft(context, command);

        assertThat(result).isEqualTo(new ApprovalConfigurationToolPort.RuleDraftResult(
                51L, "large-expense", "ENABLED", 3, now));
        verify(approvalEngineService).enableRuleDraftAgent(7L, 51L, 2);
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
                0, false, true));

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
    void mapsAttendanceReissueDecisionToVersionedDomainWrite() {
        LocalDateTime decidedAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        when(attendanceService.decideReissue(eq(7L), eq(31L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.aiworkmate.dto.AttendanceReissueResponse(
                        31L, 9L, "申请人", 7L, "当前用户", LocalDate.of(2026, 9, 30),
                        "CLOCK_IN", "忘记打卡", "REJECTED", "信息不足", decidedAt.minusDays(1),
                        decidedAt, decidedAt.minusDays(1), decidedAt, 4, false, false));

        var result = attendanceAdapter.decideReissue(context,
                new AttendanceToolPort.ReissueDecisionCommand(31L, 3, "REJECTED", "信息不足"));

        assertThat(result).isEqualTo(new AttendanceToolPort.ReissueDecisionResult(
                31L, "REJECTED", 4, decidedAt));
        var request = ArgumentCaptor.forClass(com.aiworkmate.dto.AttendanceReissueDecisionRequest.class);
        verify(attendanceService).decideReissue(eq(7L), eq(31L), request.capture());
        assertThat(request.getValue()).isEqualTo(
                new com.aiworkmate.dto.AttendanceReissueDecisionRequest(3, "REJECTED", "信息不足"));
    }

    @Test
    void mapsSelfAttendanceClockWithoutInventingClientIpOrTimestamp() {
        LocalDate day = LocalDate.of(2026, 9, 30);
        LocalDateTime clockIn = day.atTime(9, 0);
        when(attendanceService.clock(7L, new com.aiworkmate.dto.AttendanceClockRequest("CLOCK_IN"), null))
                .thenReturn(new com.aiworkmate.dto.AttendanceClockResponse(
                        81L, day, clockIn, null, "NORMAL", 0, 0));

        assertThat(attendanceAdapter.clock(context, "CLOCK_IN")).isEqualTo(
                new AttendanceToolPort.ClockWriteResult(
                        81L, day, clockIn, null, "NORMAL", 0, 0));
        verify(attendanceService).clock(
                7L, new com.aiworkmate.dto.AttendanceClockRequest("CLOCK_IN"), null);
    }

    @Test
    void mapsAttendanceSettingsUpdateWithoutAcceptingTenantIdentity() {
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 1, 18, 10);
        var command = new AttendanceToolPort.SettingsUpdateCommand(
                2, LocalTime.of(8, 30), LocalTime.of(17, 30), 20, 10, true);
        when(attendanceService.updateSettings(eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.aiworkmate.dto.AttendanceSettingsResponse(
                        1L, LocalTime.of(8, 30), LocalTime.of(17, 30), 20, 10, true, 3, updatedAt));

        assertThat(attendanceAdapter.updateSettings(context, command)).isEqualTo(
                new AttendanceToolPort.SettingsUpdateResult(
                        3, LocalTime.of(8, 30), LocalTime.of(17, 30), 20, 10, true, updatedAt));
        var request = ArgumentCaptor.forClass(com.aiworkmate.dto.AttendanceSettingsRequest.class);
        verify(attendanceService).updateSettings(eq(7L), request.capture());
        assertThat(request.getValue()).isEqualTo(new com.aiworkmate.dto.AttendanceSettingsRequest(
                2, LocalTime.of(8, 30), LocalTime.of(17, 30), 20, 10, true));
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
    void mapsVisitorLeaveAndReadOnlyVerificationToTheSharedVisitContract() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 20, 11, 0);
        var command = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitCommand(
                31, 4, "前台确认离场");
        var domain = new com.aiworkmate.service.model.VisitorAgentVisitCommand(
                31, 4, "前台确认离场");
        var receipt = new com.aiworkmate.service.model.VisitorAgentVisitReceipt(
                31, "LEFT", 5, occurredAt);
        when(adminAssetsService.leaveVisitorAgent(7L, domain, "operation-4"))
                .thenReturn(receipt);
        when(adminAssetsService.findAgentVisitorLeave(7L, domain, "operation-4"))
                .thenReturn(java.util.Optional.of(receipt));

        var expected = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitResult(
                31, "LEFT", 5, occurredAt);
        assertThat(visitorAdapter.leave(context, command, new ToolOperationKey("operation-4")))
                .isEqualTo(expected);
        assertThat(visitorAdapter.findLeave(context, command, new ToolOperationKey("operation-4")))
                .isEqualTo(com.aiworkmate.agent.tool.port.ToolWriteVerification.observed(expected));
        verify(adminAssetsService).leaveVisitorAgent(7L, domain, "operation-4");
        verify(adminAssetsService).findAgentVisitorLeave(7L, domain, "operation-4");
    }

    @Test
    void mapsVisitorWithdrawAndNoShowWithoutAcceptingAnActorFromArguments() {
        VisitorBookingResponse withdrawn = mock(VisitorBookingResponse.class);
        when(withdrawn.id()).thenReturn(31L);
        when(withdrawn.status()).thenReturn("WITHDRAWN");
        when(withdrawn.version()).thenReturn(3);
        when(adminAssetsService.withdrawVisitorBooking(
                7L, 31L, new com.aiworkmate.dto.VersionRequest(2))).thenReturn(withdrawn);

        var withdrawResult = visitorAdapter.withdraw(context,
                new com.aiworkmate.agent.tool.port.VisitorToolPort.VersionCommand(31, 2));
        assertThat(withdrawResult).isEqualTo(
                new com.aiworkmate.agent.tool.port.VisitorToolPort.StatusResult(31, "WITHDRAWN", 3));

        LocalDateTime occurredAt = LocalDateTime.of(2026, 9, 30, 18, 20);
        var command = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitCommand(
                32, 4, "超过预约时间未到访");
        var domain = new com.aiworkmate.service.model.VisitorAgentVisitCommand(
                32, 4, "超过预约时间未到访");
        var receipt = new com.aiworkmate.service.model.VisitorAgentVisitReceipt(
                32, "NO_SHOW", 5, occurredAt);
        when(adminAssetsService.markVisitorNoShowAgent(7L, domain, "operation-5"))
                .thenReturn(receipt);
        when(adminAssetsService.findAgentVisitorNoShow(7L, domain, "operation-5"))
                .thenReturn(java.util.Optional.of(receipt));

        var expected = new com.aiworkmate.agent.tool.port.VisitorToolPort.VisitResult(
                32, "NO_SHOW", 5, occurredAt);
        assertThat(visitorAdapter.markNoShow(context, command, new ToolOperationKey("operation-5")))
                .isEqualTo(expected);
        assertThat(visitorAdapter.findNoShow(context, command, new ToolOperationKey("operation-5")))
                .isEqualTo(com.aiworkmate.agent.tool.port.ToolWriteVerification.observed(expected));
        verify(adminAssetsService).withdrawVisitorBooking(
                7L, 31L, new com.aiworkmate.dto.VersionRequest(2));
        verify(adminAssetsService).markVisitorNoShowAgent(7L, domain, "operation-5");
        verify(adminAssetsService).findAgentVisitorNoShow(7L, domain, "operation-5");
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
    void mapsSealApplicationAndReadOnlyVerificationToTypedDomainContracts() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 9, 20, 9, 0);
        var command = new com.aiworkmate.agent.tool.port.SealToolPort.ApplicationCommand(
                "OFFICIAL", "采购合同", "签约", 2);
        var domain = new com.aiworkmate.service.model.SealAgentApplicationCommand(
                "OFFICIAL", "采购合同", "签约", 2);
        var receipt = new com.aiworkmate.service.model.SealAgentApplicationReceipt(
                41, "PENDING", 0, submittedAt);
        when(adminAssetsService.submitSealUsageAgent(7L, domain, "seal-operation"))
                .thenReturn(receipt);
        when(adminAssetsService.findAgentSealUsage(7L, domain, "seal-operation"))
                .thenReturn(java.util.Optional.of(receipt));

        var expected = new com.aiworkmate.agent.tool.port.SealToolPort.ApplicationResult(
                41, "PENDING", 0, submittedAt);
        assertThat(sealAdapter.apply(context, command, new ToolOperationKey("seal-operation")))
                .isEqualTo(expected);
        assertThat(sealAdapter.findApplication(context, command, new ToolOperationKey("seal-operation")))
                .isEqualTo(com.aiworkmate.agent.tool.port.ToolWriteVerification.observed(expected));
        verify(adminAssetsService).submitSealUsageAgent(7L, domain, "seal-operation");
        verify(adminAssetsService).findAgentSealUsage(7L, domain, "seal-operation");
    }

    @Test
    void mapsSealUseRegistrationAndReadOnlyVerificationToTypedDomainContracts() {
        LocalDateTime usedAt = LocalDateTime.of(2026, 9, 20, 10, 0);
        var command = new com.aiworkmate.agent.tool.port.SealToolPort.UseCommand(
                41, 2, 2, "现场核对");
        var domain = new com.aiworkmate.service.model.SealAgentUseCommand(
                41, 2, 2, "现场核对");
        var receipt = new com.aiworkmate.service.model.SealAgentUseReceipt(
                41, "USED", 3, 2, usedAt);
        when(adminAssetsService.registerSealUseAgent(7L, domain, "seal-use-operation"))
                .thenReturn(receipt);
        when(adminAssetsService.findAgentRegisteredSealUse(7L, domain, "seal-use-operation"))
                .thenReturn(java.util.Optional.of(receipt));

        var expected = new com.aiworkmate.agent.tool.port.SealToolPort.UseResult(
                41, "USED", 3, 2, usedAt);
        assertThat(sealAdapter.registerUse(
                context, command, new ToolOperationKey("seal-use-operation"))).isEqualTo(expected);
        assertThat(sealAdapter.findRegisteredUse(
                context, command, new ToolOperationKey("seal-use-operation")))
                .isEqualTo(com.aiworkmate.agent.tool.port.ToolWriteVerification.observed(expected));
        verify(adminAssetsService).registerSealUseAgent(7L, domain, "seal-use-operation");
        verify(adminAssetsService).findAgentRegisteredSealUse(7L, domain, "seal-use-operation");
    }

    @Test
    void mapsSealWithdrawAndReturnThroughVersionBoundDomainRequests() {
        SealUsageResponse withdrawn = mock(SealUsageResponse.class);
        when(withdrawn.id()).thenReturn(41L);
        when(withdrawn.status()).thenReturn("WITHDRAWN");
        when(withdrawn.version()).thenReturn(3);
        when(adminAssetsService.withdrawSealUsage(
                7L, 41L, new com.aiworkmate.dto.VersionRequest(2))).thenReturn(withdrawn);

        assertThat(sealAdapter.withdraw(context,
                new com.aiworkmate.agent.tool.port.SealToolPort.VersionCommand(41, 2)))
                .isEqualTo(new com.aiworkmate.agent.tool.port.SealToolPort.StatusResult(
                        41, "WITHDRAWN", 3));

        SealUsageResponse returned = mock(SealUsageResponse.class);
        when(returned.id()).thenReturn(42L);
        when(returned.status()).thenReturn("RETURNED");
        when(returned.version()).thenReturn(5);
        var returnRequest = new com.aiworkmate.dto.SealReturnRequest(4, "印章已归还");
        when(adminAssetsService.returnSeal(7L, 42L, returnRequest)).thenReturn(returned);

        assertThat(sealAdapter.returnSeal(context,
                new com.aiworkmate.agent.tool.port.SealToolPort.ReturnCommand(42, 4, "印章已归还")))
                .isEqualTo(new com.aiworkmate.agent.tool.port.SealToolPort.StatusResult(
                        42, "RETURNED", 5));
        verify(adminAssetsService).withdrawSealUsage(
                7L, 41L, new com.aiworkmate.dto.VersionRequest(2));
        verify(adminAssetsService).returnSeal(7L, 42L, returnRequest);
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
    void mapsLeaveReminderThroughTheRateLimitedDomainOperation() {
        LeaveApplicationResponse response = mock(LeaveApplicationResponse.class);
        when(response.id()).thenReturn(30L);
        when(response.status()).thenReturn("PENDING");
        when(response.version()).thenReturn(4);
        when(leaveWorkflowService.remind(7L, 30L, new com.aiworkmate.dto.VersionRequest(3)))
                .thenReturn(response);

        assertThat(leaveAdapter.remind(context, 30L, 3))
                .isEqualTo(new LeaveToolPort.StatusResult(30L, "PENDING", 4));
        verify(leaveWorkflowService).remind(7L, 30L, new com.aiworkmate.dto.VersionRequest(3));
    }

    @Test
    void mapsCompleteLeaveDraftUpdateWithExpectedVersion() {
        LeaveApplicationResponse response = mock(LeaveApplicationResponse.class);
        when(response.id()).thenReturn(30L);
        when(response.status()).thenReturn("DRAFT");
        when(response.version()).thenReturn(2);
        when(response.taskId()).thenReturn(null);
        LeaveToolPort.Draft draft = new LeaveToolPort.Draft(
                "PERSONAL", 8L, LocalDate.of(2026, 10, 1), "AM",
                LocalDate.of(2026, 10, 1), "PM", "调整事由");
        when(leaveWorkflowService.updateDraft(eq(7L), eq(30L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        assertThat(leaveAdapter.updateDraft(context, 30L, 1, draft))
                .isEqualTo(new LeaveToolPort.WriteResult(30L, "DRAFT", 2, null));
        var request = ArgumentCaptor.forClass(com.aiworkmate.dto.LeaveApplicationRequest.class);
        verify(leaveWorkflowService).updateDraft(eq(7L), eq(30L), request.capture());
        assertThat(request.getValue().version()).isOne();
        assertThat(request.getValue().reason()).isEqualTo("调整事由");
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
