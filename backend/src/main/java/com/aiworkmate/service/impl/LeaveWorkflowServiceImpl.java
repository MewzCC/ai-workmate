package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.common.AvatarUrls;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.ApprovalStatusCountResponse;
import com.aiworkmate.dto.ApproverCandidateResponse;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.LeaveApplicationView;
import com.aiworkmate.dto.LeaveApprovalContextResponse;
import com.aiworkmate.dto.LeaveApprovalContextRow;
import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.WorkflowTimelineResponse;
import com.aiworkmate.dto.WorkflowStageResponse;
import com.aiworkmate.entity.LeaveApplication;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.agent.task.AgentTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.HalfDayCalculator;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveWorkflowServiceImpl implements LeaveWorkflowService {

    private static final String BUSINESS_TYPE = "LEAVE_APPLICATION";

    private final LeaveApplicationMapper leaveMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;
    private final WorkflowActionLogMapper actionLogMapper;
    private final AgentTaskMapper agentTaskMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;
    private final NotificationService notificationService;

    @Value("${app.workflow.approval-due-hours:48}")
    private long approvalDueHours;

    @Override
    @Transactional(readOnly = true)
    public LeaveApprovalContextResponse approvalContext(Long userId) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        Long approverId = leaveMapper.resolveApprover(actor.tenantId(), actor.userId());
        LeaveApprovalContextRow row = leaveMapper.selectApprovalContext(
                actor.tenantId(), actor.userId(), approverId);
        if (row == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new LeaveApprovalContextResponse(
                row.applicantName(),
                row.departmentName(),
                row.positionName(),
                approverId,
                row.approverName(),
                approverId == null ? "UNCONFIGURED" : "DIRECT_OR_DEPARTMENT_DEFAULT",
                approverId != null,
                Math.max(0, approvalDueHours),
                AvatarUrls.build(actor.userId(), row.applicantAvatar(), row.applicantUpdatedAt())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApproverCandidateResponse> approverCandidates(
            Long userId, String keyword, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String search = normalize(keyword);
        Long recommendedId = leaveMapper.resolveApprover(actor.tenantId(), actor.userId());
        List<ApproverCandidateResponse> records = leaveMapper.selectApproverCandidates(
                actor.tenantId(), actor.userId(), recommendedId, search,
                safeSize, (safePage - 1) * safeSize).stream()
                .map(item -> new ApproverCandidateResponse(
                        item.id(), item.name(), item.departmentName(), item.positionName(),
                        item.recommended(), item.avatar(), item.updatedAt(),
                        AvatarUrls.build(item.id(), item.avatar(), item.updatedAt())))
                .toList();
        long total = leaveMapper.countApproverCandidates(
                actor.tenantId(), actor.userId(), search);
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional
    public LeaveApplicationResponse createDraft(Long userId, LeaveApplicationRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        int duration = calculateDuration(request);
        LocalDateTime now = LocalDateTime.now();
        LeaveApplication leave = new LeaveApplication();
        leave.setTenantId(actor.tenantId());
        leave.setApplicantUserId(actor.userId());
        if (request.approverUserId() != null) {
            requireEligibleApprover(actor, request.approverUserId());
        }
        leave.setApproverUserId(request.approverUserId());
        applyRequest(leave, request, duration);
        leave.setStatus("DRAFT");
        leave.setVersion(0);
        leave.setCreatedAt(now);
        leave.setUpdatedAt(now);
        leaveMapper.insert(leave);
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                leave.getId().toString(), "CREATE_DRAFT", "SUCCESS", "创建请假草稿");
        return response(actor, requireView(actor.tenantId(), leave.getId()));
    }

    @Override
    @Transactional
    public LeaveApplicationResponse createAgentDraft(Long userId, LeaveApplicationRequest request,
                                                     String operationKey) {
        if (operationKey == null || operationKey.isBlank() || operationKey.length() > 128) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        int duration = calculateDuration(request);
        if (request.approverUserId() != null) {
            requireEligibleApprover(actor, request.approverUserId());
        }
        LocalDateTime now = LocalDateTime.now();
        LeaveApplication leave = new LeaveApplication();
        leave.setTenantId(actor.tenantId());
        leave.setApplicantUserId(actor.userId());
        leave.setApproverUserId(request.approverUserId());
        applyRequest(leave, request, duration);
        leave.setStatus("DRAFT");
        leave.setVersion(0);
        leave.setAgentOperationKey(operationKey);
        leave.setCreatedAt(now);
        leave.setUpdatedAt(now);

        boolean created = leaveMapper.insertAgentDraft(leave) == 1;
        if (!created) {
            leave = leaveMapper.selectByAgentOperationKey(
                    actor.tenantId(), actor.userId(), operationKey);
            if (leave == null) {
                throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
            }
        } else {
            auditService.recordTransactional(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    leave.getId().toString(), "AGENT_CREATE_DRAFT", "SUCCESS",
                    "Agent 创建请假草稿");
        }
        return response(actor, requireView(actor.tenantId(), leave.getId()));
    }

    @Override
    @Transactional
    public LeaveApplicationResponse updateDraft(Long userId, Long id, LeaveApplicationRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        if (request.version() == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "更新草稿必须携带 version");
        }
        LeaveApplication current = requireOwnedLeave(actor, id);
        requireState(current, "DRAFT");
        int duration = calculateDuration(request);
        if (request.approverUserId() != null) {
            requireEligibleApprover(actor, request.approverUserId());
        }
        LambdaUpdateWrapper<LeaveApplication> update = new LambdaUpdateWrapper<LeaveApplication>()
                .eq(LeaveApplication::getId, id)
                .eq(LeaveApplication::getTenantId, actor.tenantId())
                .eq(LeaveApplication::getApplicantUserId, actor.userId())
                .eq(LeaveApplication::getStatus, "DRAFT")
                .eq(LeaveApplication::getVersion, request.version())
                .set(LeaveApplication::getLeaveType, request.leaveType())
                .set(LeaveApplication::getApproverUserId, request.approverUserId())
                .set(LeaveApplication::getStartDate, request.startDate())
                .set(LeaveApplication::getStartPeriod, request.startPeriod())
                .set(LeaveApplication::getEndDate, request.endDate())
                .set(LeaveApplication::getEndPeriod, request.endPeriod())
                .set(LeaveApplication::getDurationHalfDays, duration)
                .set(LeaveApplication::getReason, request.reason().trim())
                .set(LeaveApplication::getUpdatedAt, LocalDateTime.now())
                .setSql("version = version + 1");
        assertUpdated(leaveMapper.update(null, update), actor, id, "UPDATE_DRAFT");
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "UPDATE_DRAFT", "SUCCESS", "更新请假草稿");
        return response(actor, requireView(actor.tenantId(), id));
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveApplicationResponse getApplication(Long userId, Long id) {
        ResolvedUserAccess actor = requireAccess(userId);
        LeaveApplicationView view = requireView(actor.tenantId(), id);
        assertCanRead(actor, view);
        return response(actor, view);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveApplicationResponse getMine(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:read:self");
        LeaveApplicationView view = requireView(actor.tenantId(), id);
        if (!actor.userId().equals(view.applicantUserId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return response(actor, view);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeaveApplicationResponse> mine(Long userId, String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:read:self");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<LeaveApplicationResponse> records = leaveMapper.selectMine(
                        actor.tenantId(), actor.userId(), normalize(status), safeSize, offset)
                .stream().map(view -> response(actor, view)).toList();
        long total = leaveMapper.countMine(actor.tenantId(), actor.userId(), normalize(status));
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeaveApplicationResponse> adminList(Long userId, String status,
                                                            LocalDateTime from, LocalDateTime to,
                                                            String keyword, String leaveType,
                                                            int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<LeaveApplicationResponse> records = leaveMapper.selectAll(
                        actor.tenantId(), normalize(status), from, to, normalizeKeyword(keyword),
                        normalizeLeaveType(leaveType), safeSize, offset)
                .stream().map(view -> response(actor, view)).toList();
        long total = leaveMapper.countAll(actor.tenantId(), normalize(status), from, to,
                normalizeKeyword(keyword), normalizeLeaveType(leaveType));
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApprovalStatusCountResponse> adminStats(Long userId) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        return leaveMapper.selectStatusCounts(actor.tenantId());
    }

    @Override
    @Transactional
    public LeaveApplicationResponse submit(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        LeaveApplication current = requireOwnedLeave(actor, id);
        requireState(current, "DRAFT");
        return submitOwned(actor, current, id, request, false);
    }

    @Override
    @Transactional
    public LeaveApplicationResponse submitAgent(Long userId, Long id, VersionRequest request,
                                                Long agentTaskId) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        LocalDateTime taskCreatedAt = agentTaskMapper.selectCreatedAtForWriteEvidence(
                agentTaskId, actor.tenantId(), actor.userId());
        LeaveApplication current = requireOwnedLeave(actor, id);
        requireState(current, "DRAFT");
        if (taskCreatedAt == null || current.getCreatedAt() == null
                || !current.getCreatedAt().isBefore(taskCreatedAt)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
        return submitOwned(actor, current, id, request, true);
    }

    private LeaveApplicationResponse submitOwned(ResolvedUserAccess actor,
                                                 LeaveApplication current,
                                                 Long id,
                                                 VersionRequest request,
                                                 boolean agentWrite) {
        Long approverId = current.getApproverUserId();
        if (approverId == null
                || leaveMapper.countEligibleApprover(
                        actor.tenantId(), actor.userId(), approverId) != 1) {
            approverId = leaveMapper.resolveApprover(actor.tenantId(), actor.userId());
        }
        if (approverId == null
                || leaveMapper.countEligibleApprover(
                        actor.tenantId(), actor.userId(), approverId) != 1) {
            recordSubmitAudit(actor, id, "FAILURE", "未配置有效审批人", agentWrite);
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED);
        }
        Long definitionId = leaveMapper.selectLeaveDefinitionId(actor.tenantId());
        if (definitionId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "请假流程定义不可用");
        }
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<LeaveApplication> update = new LambdaUpdateWrapper<LeaveApplication>()
                .eq(LeaveApplication::getId, id)
                .eq(LeaveApplication::getTenantId, actor.tenantId())
                .eq(LeaveApplication::getApplicantUserId, actor.userId())
                .eq(LeaveApplication::getStatus, "DRAFT")
                .eq(LeaveApplication::getVersion, request.version())
                .set(LeaveApplication::getStatus, "PENDING")
                .set(LeaveApplication::getApproverUserId, approverId)
                .set(LeaveApplication::getSubmittedAt, now)
                .set(LeaveApplication::getUpdatedAt, now)
                .setSql("version = version + 1");
        assertUpdated(leaveMapper.update(null, update), actor, id, "SUBMIT");

        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantId(actor.tenantId());
        instance.setDefinitionId(definitionId);
        instance.setBusinessType(BUSINESS_TYPE);
        instance.setBusinessId(id);
        instance.setApplicantId(actor.userId());
        instance.setStatus("RUNNING");
        instance.setVersion(0);
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.insert(instance);

        WorkflowTask task = new WorkflowTask();
        task.setTenantId(actor.tenantId());
        task.setInstanceId(instance.getId());
        task.setBusinessType(BUSINESS_TYPE);
        task.setBusinessId(id);
        task.setAssigneeUserId(approverId);
        task.setStatus("PENDING");
        task.setVersion(0);
        task.setDueAt(approvalDueHours > 0 ? now.plusHours(approvalDueHours) : null);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        leaveMapper.update(null, new LambdaUpdateWrapper<LeaveApplication>()
                .eq(LeaveApplication::getId, id)
                .eq(LeaveApplication::getTenantId, actor.tenantId())
                .eq(LeaveApplication::getStatus, "PENDING")
                .set(LeaveApplication::getWorkflowInstanceId, instance.getId()));
        insertAction(actor, instance.getId(), task.getId(), "SUBMIT", "DRAFT", "PENDING", null);
        recordSubmitAudit(actor, id, "SUCCESS", "提交请假申请", agentWrite);
        // 通知审批人：有新的请假申请待审批（经 Redis 临时队列异步落库）
        if (approverId != null) {
            notificationService.publish(actor.tenantId(), approverId,
                    NotificationService.TYPE_APPROVAL,
                    "新的请假申请待审批",
                    "员工提交了请假申请，请及时处理",
                    "leave", id);
        }
        return response(actor, requireView(actor.tenantId(), id));
    }

    private void recordSubmitAudit(ResolvedUserAccess actor, Long id, String result,
                                   String summary, boolean agentWrite) {
        if (agentWrite) {
            auditService.recordTransactional(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    id.toString(), "AGENT_SUBMIT", result, summary);
        } else {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    id.toString(), "SUBMIT", result, summary);
        }
    }

    @Override
    @Transactional
    public LeaveApplicationResponse withdraw(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:withdraw");
        LeaveApplication current = requireOwnedLeave(actor, id);
        requireState(current, "PENDING");
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<LeaveApplication> leaveUpdate = new LambdaUpdateWrapper<LeaveApplication>()
                .eq(LeaveApplication::getId, id)
                .eq(LeaveApplication::getTenantId, actor.tenantId())
                .eq(LeaveApplication::getApplicantUserId, actor.userId())
                .eq(LeaveApplication::getStatus, "PENDING")
                .eq(LeaveApplication::getVersion, request.version())
                .set(LeaveApplication::getStatus, "WITHDRAWN")
                .set(LeaveApplication::getCompletedAt, now)
                .set(LeaveApplication::getUpdatedAt, now)
                .setSql("version = version + 1");
        assertUpdated(leaveMapper.update(null, leaveUpdate), actor, id, "WITHDRAW");
        WorkflowTask task = requireActiveTask(actor.tenantId(), id);
        int taskUpdated = taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                .eq(WorkflowTask::getId, task.getId())
                .eq(WorkflowTask::getStatus, "PENDING")
                .eq(WorkflowTask::getVersion, task.getVersion())
                .set(WorkflowTask::getStatus, "CANCELLED")
                .set(WorkflowTask::getCompletedAt, now)
                .set(WorkflowTask::getUpdatedAt, now)
                .setSql("version = version + 1"));
        assertUpdated(taskUpdated, actor, id, "WITHDRAW");
        completeInstance(current.getWorkflowInstanceId(), actor.tenantId(), "CANCELLED", now);
        insertAction(actor, current.getWorkflowInstanceId(), task.getId(),
                "WITHDRAW", "PENDING", "WITHDRAWN", null);
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "WITHDRAW", "SUCCESS", "撤回请假申请");
        return response(actor, requireView(actor.tenantId(), id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TodoResponse> todos(Long userId, String status, LocalDateTime from,
                                            LocalDateTime to, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "todo:read");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(50, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<TodoResponse> records = taskMapper.selectTodos(
                actor.tenantId(), actor.userId(), normalize(status), from, to, safeSize, offset).stream()
                .map(todo -> new TodoResponse(
                        todo.id(), todo.applicationId(), todo.applicantUserId(), todo.applicantName(),
                        todo.leaveType(), todo.durationHalfDays(), todo.status(), todo.version(),
                        todo.submittedAt(), todo.dueAt(), todo.overdue(),
                        todo.applicantAvatar(), todo.applicantUpdatedAt(),
                        AvatarUrls.build(todo.applicantUserId(), todo.applicantAvatar(), todo.applicantUpdatedAt())))
                .toList();
        long total = taskMapper.countTodos(
                actor.tenantId(), actor.userId(), normalize(status), from, to);
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveApplicationResponse todoDetail(Long userId, Long taskId) {
        ResolvedUserAccess actor = requireAccess(userId);
        WorkflowTask task = taskMapper.selectById(taskId);
        if (task == null || !actor.tenantId().equals(task.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        LeaveApplicationView view = requireView(actor.tenantId(), task.getBusinessId());
        assertCanRead(actor, view);
        return response(actor, view);
    }

    @Override
    @Transactional
    public LeaveApplicationResponse approve(Long userId, Long taskId, ApprovalDecisionRequest request) {
        return decide(userId, taskId, request, true);
    }

    @Override
    @Transactional
    public LeaveApplicationResponse reject(Long userId, Long taskId, ApprovalDecisionRequest request) {
        if (request.comment() == null || request.comment().isBlank()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "退回意见不能为空");
        }
        return decide(userId, taskId, request, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowTimelineResponse> timeline(Long userId, Long taskId) {
        ResolvedUserAccess actor = requireAccess(userId);
        WorkflowTask task = taskMapper.selectById(taskId);
        if (task == null || !actor.tenantId().equals(task.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        LeaveApplicationView view = requireView(actor.tenantId(), task.getBusinessId());
        assertCanRead(actor, view);
        return actionLogMapper.selectTimeline(actor.tenantId(), task.getInstanceId()).stream()
                .map(entry -> new WorkflowTimelineResponse(
                        entry.id(), entry.actorUserId(), entry.actorName(), entry.action(),
                        entry.fromStatus(), entry.toStatus(), entry.comment(), entry.createdAt(),
                        entry.actorAvatar(), entry.actorUpdatedAt(),
                        AvatarUrls.build(entry.actorUserId(), entry.actorAvatar(), entry.actorUpdatedAt())))
                .toList();
    }

    private LeaveApplicationResponse decide(Long userId,
                                            Long taskId,
                                            ApprovalDecisionRequest request,
                                            boolean approved) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:act");
        WorkflowTask task = requireAssignedTask(actor, taskId);
        if (!"PENDING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
        LeaveApplication application = leaveMapper.selectById(task.getBusinessId());
        if (application == null || !actor.tenantId().equals(application.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (actor.userId().equals(application.getApplicantUserId())) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    application.getId().toString(), approved ? "APPROVE" : "REJECT",
                    "DENIED", "申请人不能审批自己的申请");
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN, "申请人不能审批自己的申请");
        }
        LocalDateTime now = LocalDateTime.now();
        String taskStatus = approved ? "APPROVED" : "REJECTED";
        String leaveStatus = approved ? "APPROVED" : "REJECTED";
        int taskUpdated = taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                .eq(WorkflowTask::getId, taskId)
                .eq(WorkflowTask::getTenantId, actor.tenantId())
                .eq(WorkflowTask::getAssigneeUserId, actor.userId())
                .eq(WorkflowTask::getStatus, "PENDING")
                .eq(WorkflowTask::getVersion, request.version())
                .set(WorkflowTask::getStatus, taskStatus)
                .set(WorkflowTask::getDecisionComment, normalize(request.comment()))
                .set(WorkflowTask::getCompletedAt, now)
                .set(WorkflowTask::getUpdatedAt, now)
                .setSql("version = version + 1"));
        assertUpdated(taskUpdated, actor, application.getId(), approved ? "APPROVE" : "REJECT");
        int leaveUpdated = leaveMapper.update(null, new LambdaUpdateWrapper<LeaveApplication>()
                .eq(LeaveApplication::getId, application.getId())
                .eq(LeaveApplication::getTenantId, actor.tenantId())
                .eq(LeaveApplication::getStatus, "PENDING")
                .set(LeaveApplication::getStatus, leaveStatus)
                .set(LeaveApplication::getCompletedAt, now)
                .set(LeaveApplication::getUpdatedAt, now)
                .setSql("version = version + 1"));
        assertUpdated(leaveUpdated, actor, application.getId(), approved ? "APPROVE" : "REJECT");
        completeInstance(task.getInstanceId(), actor.tenantId(), "COMPLETED", now);
        insertAction(actor, task.getInstanceId(), taskId,
                approved ? "APPROVE" : "REJECT", "PENDING", leaveStatus, normalize(request.comment()));
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                application.getId().toString(), approved ? "APPROVE" : "REJECT",
                "SUCCESS", approved ? "通过请假申请" : "退回请假申请");
        // 通知申请人：审批结果（经 Redis 临时队列异步落库）
        notificationService.publish(actor.tenantId(), application.getApplicantUserId(),
                NotificationService.TYPE_APPROVAL,
                approved ? "请假申请已通过" : "请假申请已被退回",
                approved ? "你的请假申请已通过审批" : "你的请假申请被退回，请查看原因",
                "leave", application.getId());
        return response(actor, requireView(actor.tenantId(), application.getId()));
    }

    private void completeInstance(Long instanceId, Long tenantId, String status, LocalDateTime now) {
        int updated = instanceMapper.update(null, new LambdaUpdateWrapper<WorkflowInstance>()
                .eq(WorkflowInstance::getId, instanceId)
                .eq(WorkflowInstance::getTenantId, tenantId)
                .eq(WorkflowInstance::getStatus, "RUNNING")
                .set(WorkflowInstance::getStatus, status)
                .set(WorkflowInstance::getCompletedAt, now)
                .set(WorkflowInstance::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private WorkflowTask requireActiveTask(Long tenantId, Long applicationId) {
        WorkflowTask task = taskMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<WorkflowTask>()
                        .eq(WorkflowTask::getTenantId, tenantId)
                        .eq(WorkflowTask::getBusinessType, BUSINESS_TYPE)
                        .eq(WorkflowTask::getBusinessId, applicationId)
                        .eq(WorkflowTask::getStatus, "PENDING"));
        if (task == null) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        return task;
    }

    private WorkflowTask requireAssignedTask(ResolvedUserAccess actor, Long taskId) {
        WorkflowTask task = taskMapper.selectById(taskId);
        if (task == null || !actor.tenantId().equals(task.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!actor.userId().equals(task.getAssigneeUserId())) {
            auditService.record(actor.tenantId(), actor.userId(), "WORKFLOW_TASK",
                    taskId.toString(), "READ_OR_DECIDE", "DENIED", "非当前待办受理人");
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return task;
    }

    private LeaveApplication requireOwnedLeave(ResolvedUserAccess actor, Long id) {
        LeaveApplication leave = leaveMapper.selectById(id);
        if (leave == null || !actor.tenantId().equals(leave.getTenantId())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!actor.userId().equals(leave.getApplicantUserId())) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    id.toString(), "WRITE", "DENIED", "非申请资源所有者");
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        return leave;
    }

    private void assertCanRead(ResolvedUserAccess actor, LeaveApplicationView view) {
        boolean allowed = actor.userId().equals(view.applicantUserId())
                || actor.userId().equals(view.approverUserId())
                || actor.permissions().contains("audit:read");
        if (!allowed) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    view.id().toString(), "READ", "DENIED", "资源归属校验失败");
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
    }

    private LeaveApplicationResponse response(ResolvedUserAccess actor, LeaveApplicationView view) {
        boolean applicant = actor.userId().equals(view.applicantUserId());
        boolean approver = actor.userId().equals(view.approverUserId());
        return new LeaveApplicationResponse(
                view.id(), view.applicantUserId(), view.applicantName(),
                view.approverUserId(), view.approverName(), view.leaveType(),
                view.startDate(), view.startPeriod(), view.endDate(), view.endPeriod(),
                view.durationHalfDays(), view.durationHalfDays() / 2.0,
                view.reason(), view.status(), view.version(), view.taskId(), view.taskVersion(),
                view.taskStatus(), view.taskDueAt(), isOverdue(view),
                view.workflowStatus(), currentStage(view.status()), workflowStages(view),
                view.submittedAt(), view.completedAt(), view.createdAt(), view.updatedAt(),
                applicant && "DRAFT".equals(view.status()),
                applicant && "DRAFT".equals(view.status()),
                applicant && "PENDING".equals(view.status())
                        && actor.permissions().contains("leave:withdraw"),
                approver && "PENDING".equals(view.status())
                        && actor.permissions().contains("approval:act")
                        && !applicant,
                AvatarUrls.build(view.applicantUserId(), view.applicantAvatar(), view.applicantUpdatedAt()),
                AvatarUrls.build(view.approverUserId(), view.approverAvatar(), view.approverUpdatedAt())
        );
    }

    private boolean isOverdue(LeaveApplicationView view) {
        return "PENDING".equals(view.taskStatus())
                && view.taskDueAt() != null
                && view.taskDueAt().isBefore(LocalDateTime.now());
    }

    private String currentStage(String status) {
        return switch (status) {
            case "DRAFT" -> "APPLICATION";
            case "PENDING" -> "APPROVAL";
            default -> "COMPLETED";
        };
    }

    private List<WorkflowStageResponse> workflowStages(LeaveApplicationView view) {
        boolean draft = "DRAFT".equals(view.status());
        boolean pending = "PENDING".equals(view.status());
        boolean approved = "APPROVED".equals(view.status());
        boolean rejected = "REJECTED".equals(view.status());
        boolean withdrawn = "WITHDRAWN".equals(view.status());
        return List.of(
                new WorkflowStageResponse(
                        "APPLICATION",
                        "填写并提交",
                        draft ? "PROCESS" : "FINISH",
                        view.applicantName(),
                        view.submittedAt() == null ? view.createdAt() : view.submittedAt(),
                        draft ? "申请信息尚未提交" : "申请已提交并生成审批待办"
                ),
                new WorkflowStageResponse(
                        "APPROVAL",
                        "直属/部门审批",
                        pending ? "PROCESS" : draft ? "WAIT" : approved ? "FINISH" : "ERROR",
                        view.approverName(),
                        pending || draft ? null : view.completedAt(),
                        pending
                                ? (isOverdue(view) ? "当前审批已超过处理时限" : "等待当前审批人处理")
                                : approved ? "审批人已通过申请"
                                : rejected ? "审批人已退回申请"
                                : withdrawn ? "申请人撤回，审批任务已取消"
                                : "等待申请提交"
                ),
                new WorkflowStageResponse(
                        "COMPLETED",
                        "流程归档",
                        draft || pending ? "WAIT" : approved ? "FINISH" : "ERROR",
                        null,
                        view.completedAt(),
                        approved ? "流程已完成并归档"
                                : rejected ? "流程以退回结果结束"
                                : withdrawn ? "流程以撤回结果结束"
                                : "审批完成后自动归档"
                )
        );
    }

    private void insertAction(ResolvedUserAccess actor,
                              Long instanceId,
                              Long taskId,
                              String action,
                              String fromStatus,
                              String toStatus,
                              String comment) {
        WorkflowActionLog log = new WorkflowActionLog();
        log.setTenantId(actor.tenantId());
        log.setInstanceId(instanceId);
        log.setTaskId(taskId);
        log.setActorUserId(actor.userId());
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setComment(comment);
        String traceId = TraceContext.traceId();
        log.setTraceId(traceId == null ? UUID.randomUUID().toString().replace("-", "") : traceId);
        log.setCreatedAt(LocalDateTime.now());
        actionLogMapper.insert(log);
    }

    private int calculateDuration(LeaveApplicationRequest request) {
        if (request.startDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "开始日期不能早于今天");
        }
        return HalfDayCalculator.calculate(
                request.startDate(), request.startPeriod(), request.endDate(), request.endPeriod());
    }

    private void applyRequest(LeaveApplication leave,
                              LeaveApplicationRequest request,
                              int duration) {
        leave.setLeaveType(request.leaveType());
        leave.setApproverUserId(request.approverUserId());
        leave.setStartDate(request.startDate());
        leave.setStartPeriod(request.startPeriod());
        leave.setEndDate(request.endDate());
        leave.setEndPeriod(request.endPeriod());
        leave.setDurationHalfDays(duration);
        leave.setReason(request.reason().trim());
    }

    private void requireEligibleApprover(ResolvedUserAccess actor, Long approverUserId) {
        if (approverUserId == null || leaveMapper.countEligibleApprover(
                actor.tenantId(), actor.userId(), approverUserId) != 1) {
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED,
                    "所选审批人不在有效审批范围内，请重新选择");
        }
    }

    private LeaveApplicationView requireView(Long tenantId, Long id) {
        LeaveApplicationView view = leaveMapper.selectView(tenantId, id);
        if (view == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return view;
    }

    private ResolvedUserAccess requireAccess(Long userId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return access;
    }

    private ResolvedUserAccess requirePermission(Long userId, String permission) {
        ResolvedUserAccess access = requireAccess(userId);
        if (!access.permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return access;
    }

    private void requireState(LeaveApplication application, String state) {
        if (!state.equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
    }

    private void assertUpdated(int updated,
                               ResolvedUserAccess actor,
                               Long resourceId,
                               String action) {
        if (updated != 1) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    resourceId.toString(), action, "CONFLICT", "状态或版本冲突");
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeKeyword(String value) {
        String normalized = normalize(value);
        return normalized == null ? null
                : normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String normalizeLeaveType(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toUpperCase(java.util.Locale.ROOT);
    }
}
