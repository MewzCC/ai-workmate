package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.LeaveApplicationRequest;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.LeaveApplicationView;
import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.WorkflowTimelineResponse;
import com.aiworkmate.entity.LeaveApplication;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.HalfDayCalculator;
import com.aiworkmate.service.LeaveWorkflowService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;

    @Value("${app.workflow.approval-due-hours:48}")
    private long approvalDueHours;

    @Override
    @Transactional
    public LeaveApplicationResponse createDraft(Long userId, LeaveApplicationRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        int duration = calculateDuration(request);
        LocalDateTime now = LocalDateTime.now();
        LeaveApplication leave = new LeaveApplication();
        leave.setTenantId(actor.tenantId());
        leave.setApplicantUserId(actor.userId());
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
    public LeaveApplicationResponse updateDraft(Long userId, Long id, LeaveApplicationRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        if (request.version() == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "更新草稿必须携带 version");
        }
        LeaveApplication current = requireOwnedLeave(actor, id);
        requireState(current, "DRAFT");
        int duration = calculateDuration(request);
        LambdaUpdateWrapper<LeaveApplication> update = new LambdaUpdateWrapper<LeaveApplication>()
                .eq(LeaveApplication::getId, id)
                .eq(LeaveApplication::getTenantId, actor.tenantId())
                .eq(LeaveApplication::getApplicantUserId, actor.userId())
                .eq(LeaveApplication::getStatus, "DRAFT")
                .eq(LeaveApplication::getVersion, request.version())
                .set(LeaveApplication::getLeaveType, request.leaveType())
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
    public PageResponse<LeaveApplicationResponse> mine(Long userId, String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:read:self");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<LeaveApplicationResponse> records = leaveMapper.selectMine(
                        actor.tenantId(), actor.userId(), normalize(status), safeSize, offset)
                .stream().map(view -> response(actor, view)).toList();
        long total = leaveMapper.countMine(actor.tenantId(), actor.userId(), normalize(status));
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional
    public LeaveApplicationResponse submit(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "leave:create");
        LeaveApplication current = requireOwnedLeave(actor, id);
        requireState(current, "DRAFT");
        Long approverId = leaveMapper.resolveApprover(actor.tenantId(), actor.userId());
        if (approverId == null) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    id.toString(), "SUBMIT", "FAILURE", "未配置有效审批人");
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
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "SUBMIT", "SUCCESS", "提交请假申请");
        return response(actor, requireView(actor.tenantId(), id));
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
        ResolvedUserAccess actor = requireAccess(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<TodoResponse> records = taskMapper.selectTodos(
                actor.tenantId(), actor.userId(), normalize(status), from, to, safeSize, offset);
        long total = taskMapper.countTodos(
                actor.tenantId(), actor.userId(), normalize(status), from, to);
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveApplicationResponse todoDetail(Long userId, Long taskId) {
        ResolvedUserAccess actor = requireAccess(userId);
        WorkflowTask task = requireAssignedTask(actor, taskId);
        return response(actor, requireView(actor.tenantId(), task.getBusinessId()));
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
        return List.copyOf(actionLogMapper.selectTimeline(actor.tenantId(), task.getInstanceId()));
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
                view.submittedAt(), view.completedAt(), view.createdAt(), view.updatedAt(),
                applicant && "DRAFT".equals(view.status()),
                applicant && "DRAFT".equals(view.status()),
                applicant && "PENDING".equals(view.status())
                        && actor.permissions().contains("leave:withdraw"),
                approver && "PENDING".equals(view.status())
                        && actor.permissions().contains("approval:act")
                        && !applicant
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
        return HalfDayCalculator.calculate(
                request.startDate(), request.startPeriod(), request.endDate(), request.endPeriod());
    }

    private void applyRequest(LeaveApplication leave,
                              LeaveApplicationRequest request,
                              int duration) {
        leave.setLeaveType(request.leaveType());
        leave.setStartDate(request.startDate());
        leave.setStartPeriod(request.startPeriod());
        leave.setEndDate(request.endDate());
        leave.setEndPeriod(request.endPeriod());
        leave.setDurationHalfDays(duration);
        leave.setReason(request.reason().trim());
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
}
