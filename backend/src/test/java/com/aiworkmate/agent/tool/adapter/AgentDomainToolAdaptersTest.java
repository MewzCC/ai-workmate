package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.tool.port.ApprovalTaskToolPort;
import com.aiworkmate.agent.tool.port.LeaveToolPort;
import com.aiworkmate.agent.tool.port.TodoToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
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
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.ApprovalEngineService;
import com.aiworkmate.service.HrService;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.MeetingBookingService;
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
    @Mock private EmployeeChangeService employeeChangeService;
    @Mock private AdminAssetsService adminAssetsService;
    @Mock private MeetingBookingService meetingBookingService;

    private ApprovalAgentDomainToolAdapter approvalAdapter;
    private HrAgentDomainToolAdapter hrAdapter;
    private AdministrativeAssetsAgentDomainToolAdapter administrativeAssetsAdapter;
    private KnowledgeAgentDomainToolAdapter knowledgeAdapter;
    private NotificationAgentDomainToolAdapter notificationAdapter;
    private final ToolActorContext context = new ToolActorContext(91L, 7L, 10L, 20L, 1, "trace");

    @BeforeEach
    void setUp() {
        approvalAdapter = new ApprovalAgentDomainToolAdapter(leaveWorkflowService, approvalEngineService);
        hrAdapter = new HrAgentDomainToolAdapter(hrService, employeeChangeService);
        administrativeAssetsAdapter = new AdministrativeAssetsAgentDomainToolAdapter(
                adminAssetsService, meetingBookingService);
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

        TodoToolPort.Page result = approvalAdapter.query(context,
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

        var result = approvalAdapter.query(context, new ApprovalConfigurationToolPort.Query(
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

        var result = approvalAdapter.query(context, new ApprovalTaskToolPort.Query(
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

        var result = hrAdapter.query(context, new com.aiworkmate.agent.tool.port.HrOrganizationToolPort.Query("研发", 20));

        assertThat(result.departments()).hasSize(1);
        assertThat(result.positions()).isEmpty();
        assertThat(result.employees()).isEmpty();
        assertThat(result.toString()).doesNotContain("secret@example.com", "avatar", "9L");
        verify(hrService).overviewForActor(7L);
    }

    @Test
    void keepsLeaveOperationKeyAndMapsWriteResult() {
        when(leaveWorkflowService.createAgentDraft(eq(7L), org.mockito.ArgumentMatchers.any(), eq("operation-1")))
                .thenReturn(leaveApplication());
        LeaveToolPort.Draft command = new LeaveToolPort.Draft(
                "PERSONAL", 8L, LocalDate.of(2026, 9, 15), "AM",
                LocalDate.of(2026, 9, 15), "PM", "家庭事务");

        LeaveToolPort.WriteResult result = approvalAdapter.createDraft(context, command, "operation-1");

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
