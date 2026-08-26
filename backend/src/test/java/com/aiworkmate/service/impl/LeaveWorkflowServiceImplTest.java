package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationView;
import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.entity.LeaveApplication;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.LeaveApplicationMapper;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveWorkflowServiceImplTest {

    private static final long TENANT_ID = 1L;
    private static final long APPLICANT_ID = 1001L;
    private static final long APPROVER_ID = 2001L;

    @Mock
    private LeaveApplicationMapper leaveMapper;
    @Mock
    private WorkflowInstanceMapper instanceMapper;
    @Mock
    private WorkflowTaskMapper taskMapper;
    @Mock
    private WorkflowActionLogMapper actionLogMapper;
    @Mock
    private UserAccessService userAccessService;
    @Mock
    private BusinessAuditService auditService;
    @Mock
    private NotificationService notificationService;

    private LeaveWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        initializeTableMetadata(LeaveApplication.class);
        initializeTableMetadata(WorkflowInstance.class);
        initializeTableMetadata(WorkflowTask.class);
        service = new LeaveWorkflowServiceImpl(
                leaveMapper, instanceMapper, taskMapper, actionLogMapper,
                userAccessService, auditService, notificationService);
        ReflectionTestUtils.setField(service, "approvalDueHours", 48L);
    }

    private void initializeTableMetadata(Class<?> entityType) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                entityType);
    }

    @Test
    void shouldAllowDraftWithoutApproverAndCalculateServerDuration() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        when(leaveMapper.insert(any(LeaveApplication.class))).thenAnswer(invocation -> {
            LeaveApplication leave = invocation.getArgument(0);
            leave.setId(10L);
            return 1;
        });
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("DRAFT", null, null, null, null, 0));

        var response = service.createDraft(APPLICANT_ID, request(null, LocalDate.now().plusDays(1)));

        ArgumentCaptor<LeaveApplication> captor = ArgumentCaptor.forClass(LeaveApplication.class);
        verify(leaveMapper).insert(captor.capture());
        assertThat(captor.getValue().getApproverUserId()).isNull();
        assertThat(captor.getValue().getDurationHalfDays()).isEqualTo(2);
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.canEdit()).isTrue();
        verify(leaveMapper, never()).countEligibleApprover(anyLong(), anyLong(), anyLong());
    }

    @Test
    void shouldResolveFallbackApproverAndCreateSinglePendingTaskOnSubmit() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        LeaveApplication draft = leave("DRAFT", null, 0);
        when(leaveMapper.selectById(10L)).thenReturn(draft);
        when(leaveMapper.resolveApprover(TENANT_ID, APPLICANT_ID)).thenReturn(APPROVER_ID);
        when(leaveMapper.countEligibleApprover(TENANT_ID, APPLICANT_ID, APPROVER_ID)).thenReturn(1);
        when(leaveMapper.selectLeaveDefinitionId(TENANT_ID)).thenReturn(5L);
        when(leaveMapper.update(isNull(), any())).thenReturn(1);
        when(instanceMapper.insert(any(WorkflowInstance.class))).thenAnswer(invocation -> {
            WorkflowInstance instance = invocation.getArgument(0);
            instance.setId(20L);
            return 1;
        });
        when(taskMapper.insert(any(WorkflowTask.class))).thenAnswer(invocation -> {
            WorkflowTask task = invocation.getArgument(0);
            task.setId(30L);
            return 1;
        });
        when(actionLogMapper.insert(any(WorkflowActionLog.class))).thenReturn(1);
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1));

        var response = service.submit(APPLICANT_ID, 10L, new VersionRequest(0));

        ArgumentCaptor<WorkflowTask> taskCaptor = ArgumentCaptor.forClass(WorkflowTask.class);
        verify(taskMapper).insert(taskCaptor.capture());
        WorkflowTask task = taskCaptor.getValue();
        assertThat(task.getAssigneeUserId()).isEqualTo(APPROVER_ID);
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getDueAt()).isAfter(LocalDateTime.now().plusHours(47));
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.currentStage()).isEqualTo("APPROVAL");

        ArgumentCaptor<WorkflowActionLog> logCaptor = ArgumentCaptor.forClass(WorkflowActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("SUBMIT");
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("PENDING");
    }

    @Test
    void shouldRejectPastStartDateOnServer() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());

        assertThatThrownBy(() -> service.createDraft(
                APPLICANT_ID, request(null, LocalDate.now().minusDays(1))))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("REQUEST_INVALID"));

        verify(leaveMapper, never()).insert(any(LeaveApplication.class));
    }

    @Test
    void shouldAllowApplicantToReadOwnTodoDetail() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        WorkflowTask task = pendingTask();
        task.setAssigneeUserId(APPROVER_ID);
        when(taskMapper.selectById(30L)).thenReturn(task);
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1));

        var response = service.todoDetail(APPLICANT_ID, 30L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.canApprove()).isFalse();
    }

    @Test
    void shouldRejectTodoDetailForUnrelatedUser() {
        when(userAccessService.resolveActiveUser(9999L)).thenReturn(unrelatedAccess());
        WorkflowTask task = pendingTask();
        task.setAssigneeUserId(APPROVER_ID);
        when(taskMapper.selectById(30L)).thenReturn(task);
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1));

        assertThatThrownBy(() -> service.todoDetail(9999L, 30L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("RESOURCE_FORBIDDEN"));
    }

    @Test
    void shouldReturnVersionConflictWhenApprovalWasAlreadyProcessed() {
        when(userAccessService.resolveActiveUser(APPROVER_ID)).thenReturn(approverAccess());
        WorkflowTask task = pendingTask();
        when(taskMapper.selectById(30L)).thenReturn(task);
        when(leaveMapper.selectById(10L)).thenReturn(leave("PENDING", APPROVER_ID, 1));
        when(taskMapper.update(isNull(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.approve(
                APPROVER_ID, 30L, new ApprovalDecisionRequest(0, "同意")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("VERSION_CONFLICT"));

        verify(leaveMapper, never()).update(isNull(), any());
    }

    @Test
    void shouldApprovePendingTaskAndCompleteWorkflow() {
        when(userAccessService.resolveActiveUser(APPROVER_ID)).thenReturn(approverAccess());
        WorkflowTask task = pendingTask();
        when(taskMapper.selectById(30L)).thenReturn(task);
        when(leaveMapper.selectById(10L)).thenReturn(leave("PENDING", APPROVER_ID, 1));
        when(taskMapper.update(isNull(), any())).thenReturn(1);
        when(leaveMapper.update(isNull(), any())).thenReturn(1);
        when(instanceMapper.update(isNull(), any())).thenReturn(1);
        when(actionLogMapper.insert(any(WorkflowActionLog.class))).thenReturn(1);
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("APPROVED", APPROVER_ID, 30L, 1, "APPROVED", 2));

        var response = service.approve(
                APPROVER_ID, 30L, new ApprovalDecisionRequest(0, "同意"));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.currentStage()).isEqualTo("COMPLETED");
        assertThat(response.canApprove()).isFalse();
        ArgumentCaptor<WorkflowActionLog> logCaptor = ArgumentCaptor.forClass(WorkflowActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("APPROVE");
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("APPROVED");
    }

    @Test
    void shouldRequireCommentWhenRejectingApplication() {
        assertThatThrownBy(() -> service.reject(
                APPROVER_ID, 30L, new ApprovalDecisionRequest(0, " ")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("REQUEST_INVALID"));

        verify(taskMapper, never()).selectById(anyLong());
    }

    @Test
    void shouldWithdrawPendingApplicationAndCancelOriginalTask() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        LeaveApplication application = leave("PENDING", APPROVER_ID, 1);
        application.setWorkflowInstanceId(20L);
        when(leaveMapper.selectById(10L)).thenReturn(application);
        when(leaveMapper.update(isNull(), any())).thenReturn(1);
        when(taskMapper.selectOne(any())).thenReturn(pendingTask());
        when(taskMapper.update(isNull(), any())).thenReturn(1);
        when(instanceMapper.update(isNull(), any())).thenReturn(1);
        when(actionLogMapper.insert(any(WorkflowActionLog.class))).thenReturn(1);
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("WITHDRAWN", APPROVER_ID, 30L, 1, "CANCELLED", 2));

        var response = service.withdraw(APPLICANT_ID, 10L, new VersionRequest(1));

        assertThat(response.status()).isEqualTo("WITHDRAWN");
        assertThat(response.currentStage()).isEqualTo("COMPLETED");
        assertThat(response.canWithdraw()).isFalse();
        ArgumentCaptor<WorkflowActionLog> logCaptor = ArgumentCaptor.forClass(WorkflowActionLog.class);
        verify(actionLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getAction()).isEqualTo("WITHDRAW");
        assertThat(logCaptor.getValue().getToStatus()).isEqualTo("WITHDRAWN");
    }

    @Test
    void shouldListAllApplicationsForApprovalManager() {
        when(userAccessService.resolveActiveUser(APPROVER_ID)).thenReturn(approverAccess());
        when(leaveMapper.selectAll(TENANT_ID, null, null, null, null, null, 20, 0))
                .thenReturn(List.of(
                        view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1),
                        view("APPROVED", APPROVER_ID, 31L, 1, "APPROVED", 2)));
        when(leaveMapper.countAll(TENANT_ID, null, null, null, null, null)).thenReturn(2L);

        var response = service.adminList(APPROVER_ID, null, null, null, null, null, 1, 20);

        assertThat(response.total()).isEqualTo(2);
        assertThat(response.records()).hasSize(2);
        assertThat(response.records().get(0).status()).isEqualTo("PENDING");
        assertThat(response.records().get(1).status()).isEqualTo("APPROVED");
        verify(leaveMapper).selectAll(TENANT_ID, null, null, null, null, null, 20, 0);
        verify(leaveMapper).countAll(TENANT_ID, null, null, null, null, null);
    }

    @Test
    void shouldListApplicationsWithKeywordAndLeaveTypeFilters() {
        when(userAccessService.resolveActiveUser(APPROVER_ID)).thenReturn(approverAccess());
        when(leaveMapper.selectAll(TENANT_ID, "pending", null, null, "张", "ANNUAL", 20, 0))
                .thenReturn(List.of(view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1)));
        when(leaveMapper.countAll(TENANT_ID, "pending", null, null, "张", "ANNUAL")).thenReturn(1L);

        var response = service.adminList(APPROVER_ID, "pending", null, null, " 张 ", "annual", 1, 20);

        assertThat(response.total()).isEqualTo(1);
        verify(leaveMapper).selectAll(TENANT_ID, "pending", null, null, "张", "ANNUAL", 20, 0);
        verify(leaveMapper).countAll(TENANT_ID, "pending", null, null, "张", "ANNUAL");
    }

    @Test
    void shouldSanitizeKeywordWildcards() {
        when(userAccessService.resolveActiveUser(APPROVER_ID)).thenReturn(approverAccess());
        when(leaveMapper.selectAll(TENANT_ID, null, null, null, "100\\%", null, 20, 0))
                .thenReturn(List.of());
        when(leaveMapper.countAll(TENANT_ID, null, null, null, "100\\%", null)).thenReturn(0L);

        var response = service.adminList(APPROVER_ID, null, null, null, "100%", null, 1, 20);

        assertThat(response.records()).isEmpty();
        verify(leaveMapper).selectAll(TENANT_ID, null, null, null, "100\\%", null, 20, 0);
    }

    @Test
    void shouldRequireApprovalReadPermissionForAdminList() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());

        assertThatThrownBy(() -> service.adminList(APPLICANT_ID, null, null, null, null, null, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("PERMISSION_DENIED"));

        verify(leaveMapper, never()).selectAll(anyLong(), any(), any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void shouldCreateAgentDraftOnceAndAuditInCurrentTransaction() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        when(leaveMapper.insertAgentDraft(any(LeaveApplication.class))).thenAnswer(invocation -> {
            LeaveApplication leave = invocation.getArgument(0);
            leave.setId(10L);
            return 1;
        });
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("DRAFT", null, null, null, null, 0));

        var response = service.createAgentDraft(
                APPLICANT_ID, request(null, LocalDate.now().plusDays(1)), "agent:10:20:leave.createDraft:v1");

        assertThat(response.id()).isEqualTo(10L);
        ArgumentCaptor<LeaveApplication> captor = ArgumentCaptor.forClass(LeaveApplication.class);
        verify(leaveMapper).insertAgentDraft(captor.capture());
        assertThat(captor.getValue().getAgentOperationKey())
                .isEqualTo("agent:10:20:leave.createDraft:v1");
        verify(auditService).recordTransactional(
                TENANT_ID, APPLICANT_ID, "LEAVE_APPLICATION", "10",
                "AGENT_CREATE_DRAFT", "SUCCESS", "Agent 创建请假草稿");
    }

    @Test
    void shouldReturnExistingAgentDraftWithoutCreatingOrAuditingAgain() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        when(leaveMapper.insertAgentDraft(any(LeaveApplication.class))).thenReturn(0);
        when(leaveMapper.selectByAgentOperationKey(
                TENANT_ID, APPLICANT_ID, "agent:10:20:leave.createDraft:v1"))
                .thenReturn(leave("DRAFT", null, 0));
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("DRAFT", null, null, null, null, 0));

        var response = service.createAgentDraft(
                APPLICANT_ID, request(null, LocalDate.now().plusDays(1)), "agent:10:20:leave.createDraft:v1");

        assertThat(response.id()).isEqualTo(10L);
        verify(auditService, never()).recordTransactional(
                anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReadOnlyOwnedLeaveDetailForAgentSelfScope() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1));

        var response = service.getMine(APPLICANT_ID, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.applicantUserId()).isEqualTo(APPLICANT_ID);
        verify(leaveMapper).selectView(TENANT_ID, 10L);
    }

    @Test
    void shouldHideOtherUsersLeaveFromSelfDetail() {
        when(userAccessService.resolveActiveUser(9999L)).thenReturn(unrelatedAccess());
        when(leaveMapper.selectView(TENANT_ID, 10L))
                .thenReturn(view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1));

        assertThatThrownBy(() -> service.getMine(9999L, 10L))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("RESOURCE_NOT_FOUND"));
    }

    @Test
    void shouldQueryMineByResolvedTenantAndUserWithFiftyItemLimit() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());
        when(leaveMapper.selectMine(TENANT_ID, APPLICANT_ID, "PENDING", 50, 50))
                .thenReturn(List.of(view("PENDING", APPROVER_ID, 30L, 0, "PENDING", 1)));
        when(leaveMapper.countMine(TENANT_ID, APPLICANT_ID, "PENDING")).thenReturn(1L);

        var response = service.mine(APPLICANT_ID, "PENDING", 2, 500);

        assertThat(response.size()).isEqualTo(50);
        verify(leaveMapper).selectMine(TENANT_ID, APPLICANT_ID, "PENDING", 50, 50);
        verify(leaveMapper).countMine(TENANT_ID, APPLICANT_ID, "PENDING");
    }

    @Test
    void shouldQueryOnlyAssignedTodosInResolvedTenantAndCapPageSize() {
        when(userAccessService.resolveActiveUser(APPROVER_ID)).thenReturn(approverAccess());
        LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 31, 23, 59);
        TodoResponse todo = new TodoResponse(
                30L, 10L, APPLICANT_ID, "测试员工", "PERSONAL", 2,
                "PENDING", 0, from.plusDays(1), to.minusDays(1), false,
                null, null, null);
        when(taskMapper.selectTodos(TENANT_ID, APPROVER_ID, "PENDING", from, to, 50, 50))
                .thenReturn(List.of(todo));
        when(taskMapper.countTodos(TENANT_ID, APPROVER_ID, "PENDING", from, to)).thenReturn(1L);

        var response = service.todos(APPROVER_ID, "PENDING", from, to, 2, 500);

        assertThat(response.records()).hasSize(1);
        assertThat(response.size()).isEqualTo(50);
        verify(taskMapper).selectTodos(TENANT_ID, APPROVER_ID, "PENDING", from, to, 50, 50);
        verify(taskMapper).countTodos(TENANT_ID, APPROVER_ID, "PENDING", from, to);
    }

    @Test
    void shouldFailClosedWhenTodoPermissionIsMissing() {
        when(userAccessService.resolveActiveUser(APPLICANT_ID)).thenReturn(applicantAccess());

        assertThatThrownBy(() -> service.todos(APPLICANT_ID, null, null, null, 1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo("PERMISSION_DENIED"));

        verify(taskMapper, never()).selectTodos(
                anyLong(), anyLong(), any(), any(), any(), anyInt(), anyInt());
        verify(taskMapper, never()).countTodos(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void shouldReturnStatusCountsForApprovalManager() {
        when(userAccessService.resolveActiveUser(APPROVER_ID)).thenReturn(approverAccess());
        when(leaveMapper.selectStatusCounts(TENANT_ID))
                .thenReturn(List.of(
                        new com.aiworkmate.dto.ApprovalStatusCountResponse("PENDING", 3),
                        new com.aiworkmate.dto.ApprovalStatusCountResponse("APPROVED", 7)));

        var response = service.adminStats(APPROVER_ID);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).status()).isEqualTo("PENDING");
        assertThat(response.get(0).count()).isEqualTo(3);
        verify(leaveMapper).selectStatusCounts(TENANT_ID);
    }

    private ResolvedUserAccess applicantAccess() {
        return new ResolvedUserAccess(
                APPLICANT_ID, "employee@example.com", TENANT_ID, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of("leave:create", "leave:read:self", "leave:withdraw"),
                List.of("SELF"), 1L);
    }

    private ResolvedUserAccess approverAccess() {
        return new ResolvedUserAccess(
                APPROVER_ID, "approver@example.com", TENANT_ID, "PROCESS_ADMIN",
                List.of("PROCESS_ADMIN"), List.of("approval:act", "approval:read", "todo:read"),
                List.of("DEPARTMENT"), 1L);
    }

    private ResolvedUserAccess unrelatedAccess() {
        return new ResolvedUserAccess(
                9999L, "other@example.com", TENANT_ID, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of("leave:create", "leave:read:self"),
                List.of("SELF"), 1L);
    }

    private LeaveApplicationRequest request(Long approverId, LocalDate startDate) {
        return new LeaveApplicationRequest(
                "PERSONAL", approverId, startDate, "AM",
                startDate, "PM", "家庭事务", null);
    }

    private LeaveApplication leave(String status, Long approverId, int version) {
        LeaveApplication leave = new LeaveApplication();
        leave.setId(10L);
        leave.setTenantId(TENANT_ID);
        leave.setApplicantUserId(APPLICANT_ID);
        leave.setApproverUserId(approverId);
        leave.setStatus(status);
        leave.setVersion(version);
        leave.setStartDate(LocalDate.now().plusDays(1));
        leave.setStartPeriod("AM");
        leave.setEndDate(LocalDate.now().plusDays(1));
        leave.setEndPeriod("PM");
        leave.setDurationHalfDays(2);
        leave.setReason("家庭事务");
        return leave;
    }

    private WorkflowTask pendingTask() {
        WorkflowTask task = new WorkflowTask();
        task.setId(30L);
        task.setTenantId(TENANT_ID);
        task.setInstanceId(20L);
        task.setBusinessType("LEAVE_APPLICATION");
        task.setBusinessId(10L);
        task.setAssigneeUserId(APPROVER_ID);
        task.setStatus("PENDING");
        task.setVersion(0);
        return task;
    }

    private LeaveApplicationView view(String status,
                                      Long approverId,
                                      Long taskId,
                                      Integer taskVersion,
                                      String taskStatus,
                                      int version) {
        LocalDateTime now = LocalDateTime.now();
        return new LeaveApplicationView(
                10L, APPLICANT_ID, "测试员工", approverId,
                approverId == null ? null : "直属主管", "PERSONAL",
                LocalDate.now().plusDays(1), "AM",
                LocalDate.now().plusDays(1), "PM",
                2, "家庭事务", status, version,
                taskId, taskVersion, taskStatus,
                taskId == null ? null : now.plusHours(48),
                "PENDING".equals(status) ? "RUNNING" : null,
                "PENDING".equals(status) ? now : null,
                null, now, now,
                "avatar-key", now,
                approverId == null ? null : "approver-avatar-key",
                approverId == null ? null : now);
    }
}
