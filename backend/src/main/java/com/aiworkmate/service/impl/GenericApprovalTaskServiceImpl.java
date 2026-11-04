package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.MessageUtils;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.dto.ApprovalDecisionRequest;
import com.aiworkmate.dto.ApprovalExecutionSnapshot;
import com.aiworkmate.dto.GenericApprovalTaskDetail;
import com.aiworkmate.dto.GenericApprovalTaskItem;
import com.aiworkmate.entity.ApprovalApplication;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.GenericApprovalTaskMapper;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.GenericApprovalTaskService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.model.BusinessCandidateQuery;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenericApprovalTaskServiceImpl implements GenericApprovalTaskService {
    private static final String TYPE = "GENERIC_APPROVAL";
    private final GenericApprovalTaskMapper queries;
    private final ApprovalApplicationMapper applications;
    private final WorkflowTaskMapper tasks;
    private final WorkflowInstanceMapper instances;
    private final WorkflowActionLogMapper actions;
    private final UserMapper users;
    private final UserAccessService access;
    private final BusinessAuditService audit;
    private final NotificationService notifications;
    private final ObjectMapper json;

    @Override @Transactional(readOnly=true)
    public PageResponse<GenericApprovalTaskItem> mine(Long userId, Long expectedTenantId, String status, LocalDateTime from, LocalDateTime to, int page, int size) {
        ResolvedUserAccess actor = actor(userId, expectedTenantId);
        permission(actor, "todo:read"); new BusinessCandidateQuery(null, page, size);
        if (size > 50 || status != null && !status.isBlank() && !Set.of("PENDING", "APPROVED", "REJECTED", "CANCELLED").contains(status)
                || from != null && to != null && from.isAfter(to)) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        String normalized = status == null || status.isBlank() ? null : status;
        return PageResponse.of(queries.mine(actor.tenantId(), actor.userId(), normalized, from, to, size, (page - 1L) * size),
                queries.countMine(actor.tenantId(), actor.userId(), normalized, from, to), page, size);
    }

    @Override @Transactional(readOnly=true)
    public GenericApprovalTaskDetail detail(Long userId, Long expectedTenantId, Long taskId, int fieldPage) {
        ResolvedUserAccess actor = actor(userId, expectedTenantId);
        if (fieldPage < 1 || fieldPage > 2) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        WorkflowTask task = task(actor, taskId);
        ApprovalExecutionSnapshot snapshot = applications.selectExecution(actor.tenantId(), task.getInstanceId());
        if (snapshot == null || snapshot.formSchema() == null || snapshot.submittedAt() == null) throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.genericApproval.snapshotUnavailable");
        if (!actor.userId().equals(task.getAssigneeUserId()) && !actor.userId().equals(snapshot.applicantUserId())
                && !actor.permissions().contains("audit:read") && actions.countTargetAction(actor.tenantId(), task.getInstanceId(), actor.userId(), "CC") == 0)
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        var fields = ApprovalApplicationAgentFieldProjection.project(json, snapshot.formSchema(), snapshot.dataJson());
        WorkflowInstance instance = instances.selectById(task.getInstanceId());
        var application = applications.selectById(task.getBusinessId());
        boolean canReject = writable(actor, task, application, instance);
        String blocked = null;
        if (canReject) {
            try { if (snapshot.planJson() != null) executable(task, snapshot); }
            catch (BusinessException invalid) { canReject = false; blocked = invalid.getMessage(); }
        }
        if (canReject) {
            try { executable(task, snapshot); GenericApprovalPlan.requireRulesHandled(json, snapshot.rules(), snapshot.dataJson()); }
            catch (BusinessException unsupported) { blocked = unsupported.getMessage(); }
        }
        String businessStatus = "CANCELLED".equals(instance.getStatus()) ? "WITHDRAWN"
                : "RUNNING".equals(instance.getStatus()) ? "PENDING"
                : tasks.selectCount(new LambdaQueryWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId, actor.tenantId())
                    .eq(WorkflowTask::getInstanceId, instance.getId()).eq(WorkflowTask::getBusinessType, TYPE).eq(WorkflowTask::getStatus, "REJECTED")) > 0 ? "REJECTED" : "APPROVED";
        int start = Math.min(fields.size(), (fieldPage - 1) * 50);
        return new GenericApprovalTaskDetail(queries.item(actor.tenantId(), taskId), instance.getStatus(), businessStatus,
                fields.subList(start, Math.min(fields.size(), start + 50)), fields.size(), fieldPage,
                canReject && blocked == null, canReject, blocked, actions.selectTimeline(actor.tenantId(), task.getInstanceId()));
    }

    @Override @Transactional
    public GenericApprovalTaskDetail approve(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request) {
        return decide(userId, expectedTenantId, taskId, request, true);
    }
    @Override @Transactional
    public GenericApprovalTaskDetail reject(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request) {
        return decide(userId, expectedTenantId, taskId, request, false);
    }
    private GenericApprovalTaskDetail decide(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request, boolean approved) {
        ResolvedUserAccess actor = actor(userId, expectedTenantId); permission(actor, "approval:act");
        if (request == null || request.version() == null || request.version() < 0 || request.comment() != null && request.comment().length() > 500)
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        if (!approved && (request.comment() == null || request.comment().isBlank()))
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.genericApproval.commentRequired");
        WorkflowTask task = task(actor, taskId);
        if (!actor.userId().equals(task.getAssigneeUserId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        // Every application mutation starts with the same aggregate lock/order, including reminder and withdrawal.
        var application = applications.lockApplication(actor.tenantId(), task.getBusinessId());
        if (application != null && actor.userId().equals(application.getApplicantUserId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        task = task(actor, taskId);
        WorkflowInstance instance = instances.selectById(task.getInstanceId());
        if (!writable(actor, task, application, instance) || !Objects.equals(task.getVersion(), request.version())) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        ApprovalExecutionSnapshot snapshot = applications.selectExecution(actor.tenantId(), task.getInstanceId());
        if (snapshot == null || snapshot.formSchema() == null || snapshot.submittedAt() == null) throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.genericApproval.snapshotUnavailable");
        if (snapshot.planJson() != null) executable(task, snapshot);
        GenericApprovalPlan.Step next = null;
        if (approved) {
            GenericApprovalPlan plan = executable(task, snapshot);
            GenericApprovalPlan.requireRulesHandled(json, snapshot.rules(), snapshot.dataJson());
            if (snapshot.currentNodeIndex() + 1 < plan.steps().size()) {
                next = plan.steps().get(snapshot.currentNodeIndex() + 1);
                User target = users.selectById(next.assigneeUserId());
                ResolvedUserAccess targetAccess = null;
                if (target != null && Integer.valueOf(1).equals(target.getStatus()) && actor.tenantId().equals(target.getTenantId())) {
                    try { targetAccess = access.resolveActiveUser(target.getId()); }
                    catch (BusinessException unavailable) { throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.genericApproval.nextApproverUnavailable"); }
                }
                if (target == null || !Integer.valueOf(1).equals(target.getStatus()) || !actor.tenantId().equals(target.getTenantId())
                        || targetAccess == null || !actor.tenantId().equals(targetAccess.tenantId()) || !targetAccess.permissions().contains("approval:act")
                        || target.getId().equals(application.getApplicantUserId()))
                    throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.genericApproval.nextApproverUnavailable");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String taskStatus = approved ? "APPROVED" : "REJECTED", businessStatus = !approved ? "REJECTED" : next == null ? "APPROVED" : "PENDING";
        one(applications.update(null, new LambdaUpdateWrapper<ApprovalApplication>().eq(ApprovalApplication::getTenantId, actor.tenantId())
                .eq(ApprovalApplication::getId, application.getId()).eq(ApprovalApplication::getWorkflowInstanceId, instance.getId())
                .eq(ApprovalApplication::getStatus, "PENDING").eq(ApprovalApplication::getVersion, application.getVersion())
                .set(ApprovalApplication::getStatus, businessStatus).set(ApprovalApplication::getCompletedAt, next == null ? now : null)
                .set(ApprovalApplication::getUpdatedAt, now).setSql("version=version+1")));
        one(tasks.update(null, new LambdaUpdateWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId, actor.tenantId()).eq(WorkflowTask::getId, taskId)
                .eq(WorkflowTask::getInstanceId, instance.getId()).eq(WorkflowTask::getBusinessType, TYPE).eq(WorkflowTask::getBusinessId, application.getId())
                .eq(WorkflowTask::getStatus, "PENDING").eq(WorkflowTask::getVersion, request.version()).set(WorkflowTask::getStatus, taskStatus)
                .set(WorkflowTask::getDecisionComment, request.comment() == null ? null : request.comment().trim()).set(WorkflowTask::getCompletedAt, now)
                .set(WorkflowTask::getUpdatedAt, now).setSql("version=version+1")));
        one(applications.advanceExecution(actor.tenantId(), instance.getId(), snapshot.version(), approved ? snapshot.currentNodeIndex() + 1 : snapshot.currentNodeIndex()));
        one(instances.update(null, new LambdaUpdateWrapper<WorkflowInstance>().eq(WorkflowInstance::getId, instance.getId())
                .eq(WorkflowInstance::getTenantId, actor.tenantId()).eq(WorkflowInstance::getStatus, "RUNNING").eq(WorkflowInstance::getVersion, instance.getVersion())
                .set(WorkflowInstance::getStatus, next == null ? "COMPLETED" : "RUNNING").set(WorkflowInstance::getCompletedAt, next == null ? now : null)
                .set(WorkflowInstance::getUpdatedAt, now).setSql("version=version+1")));
        if (next != null) {
            WorkflowTask following = new WorkflowTask(); following.setTenantId(actor.tenantId()); following.setInstanceId(instance.getId());
            following.setBusinessType(TYPE); following.setBusinessId(application.getId()); following.setAssigneeUserId(next.assigneeUserId());
            following.setProcessNodeIndex(next.index()); following.setProcessNodeName(next.name()); following.setStatus("PENDING"); following.setVersion(0);
            following.setDueAt(next.dueHours() == 0 ? null : now.plusHours(next.dueHours())); following.setCreatedAt(now); following.setUpdatedAt(now); tasks.insert(following);
        }
        WorkflowActionLog log = new WorkflowActionLog(); log.setTenantId(actor.tenantId()); log.setInstanceId(instance.getId()); log.setTaskId(taskId);
        log.setActorUserId(actor.userId()); log.setAction(approved ? "APPROVE" : "REJECT"); log.setFromStatus("PENDING"); log.setToStatus(taskStatus);
        log.setComment(request.comment() == null ? null : request.comment().trim()); log.setCreatedAt(now);
        log.setTraceId(TraceContext.traceId() == null ? UUID.randomUUID().toString().replace("-", "") : TraceContext.traceId()); actions.insert(log);
        audit.recordTransactional(actor.tenantId(), actor.userId(), TYPE, application.getId().toString(), log.getAction(), "SUCCESS",
                MessageUtils.resolve("genericApproval.auditDecision", taskId));
        notifications.publishTransactional(actor.tenantId(), next == null ? application.getApplicantUserId() : next.assigneeUserId(), NotificationService.TYPE_APPROVAL,
                MessageUtils.resolve("genericApproval.notificationTitle"), MessageUtils.resolve(next == null ? "genericApproval.notificationResult" : "genericApproval.notificationPending", snapshot.formName()),
                "generic-approval", application.getId());
        return detail(userId, expectedTenantId, taskId, 1);
    }
    private GenericApprovalPlan executable(WorkflowTask task, ApprovalExecutionSnapshot snapshot) {
        GenericApprovalPlan plan = GenericApprovalPlan.decode(json, snapshot.planJson());
        if (snapshot.currentNodeIndex() >= plan.steps().size() || task.getProcessNodeIndex() == null || task.getProcessNodeIndex() != snapshot.currentNodeIndex()
                || plan.steps().get(snapshot.currentNodeIndex()).assigneeUserId() != task.getAssigneeUserId()
                || task.getParentTaskId() != null || task.getAddSignMode() != null) throw GenericApprovalPlan.unsupported();
        return plan;
    }
    private boolean writable(ResolvedUserAccess actor, WorkflowTask task, ApprovalApplication application, WorkflowInstance instance) {
        return application != null && instance != null && actor.permissions().contains("approval:act") && actor.userId().equals(task.getAssigneeUserId())
                && !actor.userId().equals(application.getApplicantUserId()) && actor.tenantId().equals(application.getTenantId())
                && actor.tenantId().equals(instance.getTenantId()) && TYPE.equals(instance.getBusinessType())
                && task.getBusinessId().equals(instance.getBusinessId()) && application.getApplicantUserId().equals(instance.getApplicantId())
                && task.getParentTaskId() == null && task.getAddSignMode() == null
                && task.getInstanceId().equals(application.getWorkflowInstanceId()) && "PENDING".equals(task.getStatus())
                && "PENDING".equals(application.getStatus()) && "RUNNING".equals(instance.getStatus());
    }
    private WorkflowTask task(ResolvedUserAccess actor, Long id) {
        if (id == null || id < 1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        WorkflowTask task = tasks.selectGenericTaskForRead(actor.tenantId(), id);
        if (task == null) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND); return task;
    }
    private ResolvedUserAccess actor(Long userId, Long expectedTenant) {
        ResolvedUserAccess actor = access.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (expectedTenant != null && !expectedTenant.equals(actor.tenantId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN); return actor;
    }
    private void permission(ResolvedUserAccess actor, String code) { if (!actor.permissions().contains(code)) throw new BusinessException(ErrorCode.PERMISSION_DENIED); }
    private void one(int count) { if (count != 1) throw new BusinessException(ErrorCode.VERSION_CONFLICT); }
}
