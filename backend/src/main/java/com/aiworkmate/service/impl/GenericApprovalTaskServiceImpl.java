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
        return readDetail(actor, taskId, fieldPage, 50, false);
    }

    @Override @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public GenericApprovalTaskDetail detailAssignedForAgent(Long userId, Long expectedTenantId, Long taskId, int fieldPage) {
        ResolvedUserAccess actor = actor(userId, expectedTenantId);
        permission(actor, "todo:read");
        return readDetail(actor, taskId, fieldPage, AGENT_FIELD_PAGE_SIZE, true);
    }

    private GenericApprovalTaskDetail readDetail(ResolvedUserAccess actor, Long taskId, int fieldPage, int pageSize, boolean assignedOnly) {
        if (fieldPage < 1 || fieldPage > (100 + pageSize - 1) / pageSize) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        WorkflowTask task = task(actor, taskId);
        if (assignedOnly && !actor.userId().equals(task.getAssigneeUserId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        var application = applications.selectById(task.getBusinessId());
        ApprovalExecutionSnapshot snapshot = snapshot(actor, task, application);
        if (!actor.userId().equals(task.getAssigneeUserId()) && !actor.userId().equals(snapshot.applicantUserId())
                && !actor.permissions().contains("audit:read") && (!actor.permissions().contains("todo:read") || actions.countTargetAction(actor.tenantId(), task.getInstanceId(), actor.userId(), "CC") == 0))
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        var fields = ApprovalApplicationAgentFieldProjection.project(json, snapshot.formSchema(), snapshot.dataJson());
        WorkflowInstance instance = instances.selectById(task.getInstanceId());
        boolean canReject = writable(actor, task, application, instance);
        boolean canAddSign=false;
        String blocked = null;
        if (canReject) {
            try {
                if(snapshot.planJson()!=null){var runtime=runtime(task,snapshot);if(!runtime.active().getId().equals(taskId))throw GenericApprovalPlan.unsupported();canAddSign=runtime.canAddSign();}
                else if(task.getParentTaskId()!=null || task.getAddSignMode()!=null)throw GenericApprovalPlan.unsupported();
            }
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
        int start = Math.min(fields.size(), (fieldPage - 1) * pageSize);
        var additions=queries.addSignItems(actor.tenantId(),task.getInstanceId(),task.getBusinessId(),task.getParentTaskId()==null?taskId:task.getParentTaskId());
        if(additions.size()>GenericApprovalTaskRuntime.MAX_ADDED_PER_NODE)throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.genericApproval.runtimeInvalid");
        return new GenericApprovalTaskDetail(queries.item(actor.tenantId(), taskId), instance.getStatus(), businessStatus,
                fields.subList(start, Math.min(fields.size(), start + pageSize)), fields.size(), fieldPage,
                canReject && blocked == null, canReject, blocked, actions.selectTimeline(actor.tenantId(), task.getInstanceId()),
                canReject && snapshot.planJson()!=null,canReject && snapshot.planJson()!=null,canReject && blocked==null && canAddSign,additions);
    }

    @Override @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<com.aiworkmate.dto.ApprovalParticipantResponse> participants(Long userId,Long expectedTenant,Long taskId,String purpose,BusinessCandidateQuery query){
        var actor=actor(userId,expectedTenant);permission(actor,"approval:act");
        if(!Set.of("TRANSFER","CC","ADD_SIGN").contains(purpose==null?"":purpose) || query==null || query.size()>50 || query.keyword()!=null && query.keyword().length()>64)
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        var task=task(actor,taskId);var application=applications.selectById(task.getBusinessId());
        if(!actor.userId().equals(task.getAssigneeUserId()))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        if(!writable(actor,task,application,instances.selectById(task.getInstanceId())))throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        var snapshot=snapshot(actor,task,application);var runtime=runtime(task,snapshot);
        if(!runtime.active().getId().equals(taskId))throw GenericApprovalPlan.unsupported();
        if("ADD_SIGN".equals(purpose)){
            if(!runtime.canAddSign())throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.approval.addSign.nestedForbidden");
            GenericApprovalPlan.requireRulesHandled(json,snapshot.rules(),snapshot.dataJson());
        }
        boolean approvers=!"CC".equals(purpose);
        var excluded="ADD_SIGN".equals(purpose)?runtime.ineligibleAddSignTargets():"TRANSFER".equals(purpose)?runtime.restrictedTransferTargets():Set.<Long>of();
        var exclusions=excluded.stream().sorted().toList();
        var rows=queries.participants(actor.tenantId(),"approval:act",actor.userId(),application.getApplicantUserId(),approvers,query.keyword(),query.size(),query.offset(),exclusions);
        return PageResponse.of(rows.stream().map(row->new com.aiworkmate.dto.ApprovalParticipantResponse(row.id(),row.name(),
                com.aiworkmate.common.AvatarUrls.build(row.id(),row.avatar(),row.updatedAt()),row.canApprove())).toList(),
                queries.countParticipants(actor.tenantId(),"approval:act",actor.userId(),application.getApplicantUserId(),approvers,query.keyword(),exclusions),query.page(),query.size());
    }
    @Override @Transactional
    public com.aiworkmate.dto.GenericApprovalParticipantReceipt transfer(Long userId,Long expectedTenant,Long taskId,com.aiworkmate.dto.ApprovalParticipantRequest request){
        return collaborate(userId,expectedTenant,taskId,request,true);
    }
    @Override @Transactional
    public com.aiworkmate.dto.GenericApprovalParticipantReceipt copyTo(Long userId,Long expectedTenant,Long taskId,com.aiworkmate.dto.ApprovalParticipantRequest request){
        return collaborate(userId,expectedTenant,taskId,request,false);
    }
    private com.aiworkmate.dto.GenericApprovalParticipantReceipt collaborate(Long userId,Long expectedTenant,Long taskId,com.aiworkmate.dto.ApprovalParticipantRequest request,boolean transfer){
        var actor=actor(userId,expectedTenant);permission(actor,"approval:act");
        if(request==null || request.targetUserId()==null || request.targetUserId()<1 || actor.userId().equals(request.targetUserId())
                || request.version()==null || request.version()<0 || request.version()==Integer.MAX_VALUE
                || request.reason()==null || request.reason().isBlank() || request.reason().length()>500)throw new BusinessException(ErrorCode.REQUEST_INVALID);
        var task=task(actor,taskId);
        if(!actor.userId().equals(task.getAssigneeUserId()))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        var application=applications.lockApplication(actor.tenantId(),task.getBusinessId());
        actor=actor(userId,expectedTenant);permission(actor,"approval:act");
        task=task(actor,taskId);
        if(!actor.userId().equals(task.getAssigneeUserId()))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        if(!writable(actor,task,application,instances.selectById(task.getInstanceId())) || !Objects.equals(task.getVersion(),request.version()))throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        var runtime=runtime(task,snapshot(actor,task,application));
        if(!runtime.active().getId().equals(taskId))throw GenericApprovalPlan.unsupported();
        if(transfer && runtime.restrictedTransferTargets().contains(request.targetUserId()))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN,"validation.genericApproval.duplicateSigner");
        User target=users.selectById(request.targetUserId());
        if(target==null || !actor.tenantId().equals(target.getTenantId()) || !Integer.valueOf(1).equals(target.getStatus()))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        var targetAccess=access.resolveActiveUser(target.getId());
        if(targetAccess==null || !actor.tenantId().equals(targetAccess.tenantId()) || !targetAccess.permissions().contains("todo:read")
                || transfer && (!targetAccess.permissions().contains("approval:act") || target.getId().equals(application.getApplicantUserId())))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        String action=transfer?"TRANSFER":"CC";
        if(actions.selectCount(new LambdaQueryWrapper<WorkflowActionLog>().eq(WorkflowActionLog::getTenantId,actor.tenantId()).eq(WorkflowActionLog::getTaskId,taskId)
                .eq(WorkflowActionLog::getInstanceId,task.getInstanceId()).eq(WorkflowActionLog::getAction,action))>=32)throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.genericApproval.collaborationLimit");
        if(!transfer && actions.countTargetAction(actor.tenantId(),task.getInstanceId(),target.getId(),"CC")>0)throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.genericApproval.alreadyCopied");
        var now=LocalDateTime.now();
        one(applications.update(null,new LambdaUpdateWrapper<ApprovalApplication>().eq(ApprovalApplication::getTenantId,actor.tenantId()).eq(ApprovalApplication::getId,application.getId())
                .eq(ApprovalApplication::getWorkflowInstanceId,task.getInstanceId()).eq(ApprovalApplication::getStatus,"PENDING").eq(ApprovalApplication::getVersion,application.getVersion())
                .set(ApprovalApplication::getUpdatedAt,now).setSql("version=version+1")));
        one(tasks.update(null,new LambdaUpdateWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId,actor.tenantId()).eq(WorkflowTask::getId,taskId)
                .eq(WorkflowTask::getAssigneeUserId,actor.userId()).eq(WorkflowTask::getInstanceId,task.getInstanceId()).eq(WorkflowTask::getBusinessType,TYPE)
                .eq(WorkflowTask::getBusinessId,application.getId()).eq(WorkflowTask::getStatus,"PENDING").eq(WorkflowTask::getVersion,request.version())
                .set(transfer,WorkflowTask::getAssigneeUserId,target.getId()).set(WorkflowTask::getUpdatedAt,now).setSql("version=version+1")));
        var log=new WorkflowActionLog();log.setTenantId(actor.tenantId());log.setInstanceId(task.getInstanceId());log.setTaskId(taskId);
        log.setActorUserId(actor.userId());log.setOriginalAssigneeUserId(task.getAssigneeUserId());log.setTargetUserId(target.getId());
        log.setAction(action);log.setFromStatus("PENDING");log.setToStatus("PENDING");log.setComment(request.reason().trim());log.setCreatedAt(now);
        log.setTraceId(TraceContext.traceId()==null?UUID.randomUUID().toString().replace("-",""):TraceContext.traceId());one(actions.insert(log));
        audit.recordTransactional(actor.tenantId(),actor.userId(),TYPE,application.getId().toString(),action,"SUCCESS",
                MessageUtils.resolve("genericApproval.auditParticipant",taskId,task.getAssigneeUserId(),target.getId(),request.reason().trim()));
        notifications.publishTransactional(actor.tenantId(),target.getId(),NotificationService.TYPE_APPROVAL,
                MessageUtils.resolve("genericApproval.notificationParticipantTitle"),MessageUtils.resolve(transfer?"genericApproval.notificationTransferred":"genericApproval.notificationCopied",taskId),
                "generic-approval-task",taskId);
        return new com.aiworkmate.dto.GenericApprovalParticipantReceipt(application.getId(),taskId,"PENDING",request.version()+1,"PENDING",action,
                task.getAssigneeUserId(),target.getId(),transfer?target.getId():task.getAssigneeUserId());
    }

    @Override @Transactional
    public GenericApprovalTaskDetail approve(Long userId, Long expectedTenantId, Long taskId, ApprovalDecisionRequest request) {
        return decide(userId, expectedTenantId, taskId, request, true);
    }
    @Override @Transactional
    public com.aiworkmate.dto.GenericApprovalAddSignReceipt addSign(Long userId,Long expectedTenantId,Long taskId,com.aiworkmate.dto.ApprovalAddSignRequest request){
        var actor=actor(userId,expectedTenantId);permission(actor,"approval:act");
        if(request==null || request.targetUserId()==null || request.targetUserId()<1 || request.version()==null || request.version()<0 || request.version()==Integer.MAX_VALUE
                || !Set.of("PRE","POST").contains(request.mode()==null?"":request.mode()) || request.reason()==null || request.reason().isBlank() || request.reason().length()>500)
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        var task=task(actor,taskId);
        if(!actor.userId().equals(task.getAssigneeUserId()))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        var application=applications.lockApplication(actor.tenantId(),task.getBusinessId());
        actor=actor(userId,expectedTenantId);permission(actor,"approval:act");task=task(actor,taskId);
        if(!actor.userId().equals(task.getAssigneeUserId()))throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        if(!writable(actor,task,application,instances.selectById(task.getInstanceId())) || !Objects.equals(task.getVersion(),request.version()))throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        var snapshot=snapshot(actor,task,application);var runtime=runtime(task,snapshot);
        if(!runtime.active().getId().equals(taskId) || task.getParentTaskId()!=null)throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.approval.addSign.nestedForbidden");
        if(!runtime.canAddSign())throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.genericApproval.collaborationLimit");
        GenericApprovalPlan.requireRulesHandled(json,snapshot.rules(),snapshot.dataJson());
        if(runtime.ineligibleAddSignTargets().contains(request.targetUserId()))throw new BusinessException(ErrorCode.REQUEST_INVALID,"validation.genericApproval.duplicateSigner");
        requireReviewer(actor,snapshot.applicantUserId(),request.targetUserId(),true);
        var now=LocalDateTime.now();boolean pre="PRE".equals(request.mode());String rootStatus=pre?"WAITING":"PENDING";
        updateApplicationState(actor,application,"PENDING",now,false);
        one(tasks.update(null,new LambdaUpdateWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId,actor.tenantId()).eq(WorkflowTask::getId,taskId)
                .eq(WorkflowTask::getInstanceId,task.getInstanceId()).eq(WorkflowTask::getBusinessType,TYPE).eq(WorkflowTask::getBusinessId,application.getId())
                .eq(WorkflowTask::getAssigneeUserId,actor.userId()).eq(WorkflowTask::getStatus,"PENDING").eq(WorkflowTask::getVersion,request.version())
                .set(WorkflowTask::getStatus,rootStatus).set(WorkflowTask::getUpdatedAt,now).setSql("version=version+1")));
        var added=new WorkflowTask();added.setTenantId(actor.tenantId());added.setInstanceId(task.getInstanceId());added.setBusinessType(TYPE);added.setBusinessId(application.getId());
        added.setAssigneeUserId(request.targetUserId());added.setParentTaskId(taskId);added.setAddSignMode(request.mode());added.setProcessNodeIndex(task.getProcessNodeIndex());
        added.setProcessNodeName(MessageUtils.resolve("genericApproval.addSignNode."+request.mode(),task.getProcessNodeName()));
        added.setStatus(pre?"PENDING":"WAITING");added.setVersion(0);added.setDueAt(pre?dueAt(runtime.plan().steps().get(task.getProcessNodeIndex()).dueHours(),now):null);
        added.setCreatedAt(now);added.setUpdatedAt(now);one(tasks.insert(added));
        recordTaskAction(actor,task.getInstanceId(),added.getId(),"ADD_SIGN_"+request.mode(),"PENDING",added.getStatus(),request.reason().trim(),actor.userId(),request.targetUserId(),now);
        audit.recordTransactional(actor.tenantId(),actor.userId(),TYPE,application.getId().toString(),"ADD_SIGN_"+request.mode(),"SUCCESS",
                MessageUtils.resolve("genericApproval.auditAddSign",taskId,added.getId(),request.targetUserId()));
        notifications.publishTransactional(actor.tenantId(),request.targetUserId(),NotificationService.TYPE_APPROVAL,MessageUtils.resolve("genericApproval.notificationParticipantTitle"),
                MessageUtils.resolve(pre?"genericApproval.notificationAddedPre":"genericApproval.notificationAddedPost",added.getId()),"generic-approval-task",added.getId());
        return new com.aiworkmate.dto.GenericApprovalAddSignReceipt(application.getId(),taskId,rootStatus,request.version()+1,"PENDING","ADD_SIGN",request.mode(),request.targetUserId(),
                added.getId(),added.getStatus(),0,pre?added.getId():taskId);
    }
    private void requireReviewer(ResolvedUserAccess actor,long applicantId,long targetId,boolean requireInbox){
        var user=users.selectById(targetId);
        if(user==null || !Integer.valueOf(1).equals(user.getStatus()) || !actor.tenantId().equals(user.getTenantId()) || targetId==applicantId)
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.genericApproval.nextApproverUnavailable");
        var target=access.resolveActiveUser(targetId);
        if(target==null || !actor.tenantId().equals(target.tenantId()) || !target.permissions().contains("approval:act") || requireInbox && !target.permissions().contains("todo:read"))
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,"validation.genericApproval.nextApproverUnavailable");
    }
    private LocalDateTime dueAt(int hours,LocalDateTime now){return hours==0?null:now.plusHours(hours);}
    private void updateApplicationState(ResolvedUserAccess actor,ApprovalApplication application,String status,LocalDateTime now,boolean completed){
        one(applications.update(null,new LambdaUpdateWrapper<ApprovalApplication>().eq(ApprovalApplication::getTenantId,actor.tenantId()).eq(ApprovalApplication::getId,application.getId())
                .eq(ApprovalApplication::getWorkflowInstanceId,application.getWorkflowInstanceId()).eq(ApprovalApplication::getStatus,"PENDING").eq(ApprovalApplication::getVersion,application.getVersion())
                .set(ApprovalApplication::getStatus,status).set(ApprovalApplication::getCompletedAt,completed?now:null).set(ApprovalApplication::getUpdatedAt,now).setSql("version=version+1")));
    }
    private void recordTaskAction(ResolvedUserAccess actor,long instance,long task,String action,String from,String to,String comment,Long original,Long target,LocalDateTime now){
        var log=new WorkflowActionLog();log.setTenantId(actor.tenantId());log.setInstanceId(instance);log.setTaskId(task);log.setActorUserId(actor.userId());
        log.setAction(action);log.setFromStatus(from);log.setToStatus(to);log.setComment(comment);log.setOriginalAssigneeUserId(original);log.setTargetUserId(target);log.setCreatedAt(now);
        log.setTraceId(TraceContext.traceId()==null?UUID.randomUUID().toString().replace("-",""):TraceContext.traceId());one(actions.insert(log));
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
        actor=actor(userId,expectedTenantId);permission(actor,"approval:act");
        if (application != null && actor.userId().equals(application.getApplicantUserId())) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        task = task(actor, taskId);
        WorkflowInstance instance = instances.selectById(task.getInstanceId());
        if (!writable(actor, task, application, instance) || !Objects.equals(task.getVersion(), request.version())) throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        ApprovalExecutionSnapshot snapshot = snapshot(actor, task, application);
        var runtime=snapshot.planJson()==null?null:runtime(task,snapshot);
        if(runtime!=null && !runtime.active().getId().equals(taskId) || runtime==null && task.getParentTaskId()!=null)throw GenericApprovalPlan.unsupported();
        if(approved && runtime==null)throw GenericApprovalPlan.unsupported();
        WorkflowTask resume=approved?runtime.nextAfterApproval():null;
        boolean advance=approved && resume==null;
        GenericApprovalPlan.Step next = null;
        if (approved) {
            GenericApprovalPlan plan = runtime.plan();
            GenericApprovalPlan.requireRulesHandled(json, snapshot.rules(), snapshot.dataJson());
            if(resume!=null)requireReviewer(actor,snapshot.applicantUserId(),resume.getAssigneeUserId(),true);
            if (advance && snapshot.currentNodeIndex() + 1 < plan.steps().size()) {
                next = plan.steps().get(snapshot.currentNodeIndex() + 1);
                requireReviewer(actor,snapshot.applicantUserId(),next.assigneeUserId(),false);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        boolean ended=!approved || resume==null && next==null;
        String taskStatus = approved ? "APPROVED" : "REJECTED", businessStatus = !approved ? "REJECTED" : ended ? "APPROVED" : "PENDING";
        updateApplicationState(actor,application,businessStatus,now,ended);
        one(tasks.update(null, new LambdaUpdateWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId, actor.tenantId()).eq(WorkflowTask::getId, taskId)
                .eq(WorkflowTask::getInstanceId, instance.getId()).eq(WorkflowTask::getBusinessType, TYPE).eq(WorkflowTask::getBusinessId, application.getId())
                .eq(WorkflowTask::getStatus, "PENDING").eq(WorkflowTask::getVersion, request.version()).set(WorkflowTask::getStatus, taskStatus)
                .set(WorkflowTask::getDecisionComment, request.comment() == null ? null : request.comment().trim()).set(WorkflowTask::getCompletedAt, now)
                .set(WorkflowTask::getUpdatedAt, now).setSql("version=version+1")));
        if(resume!=null){
            one(tasks.update(null,new LambdaUpdateWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId,actor.tenantId()).eq(WorkflowTask::getId,resume.getId())
                    .eq(WorkflowTask::getInstanceId,instance.getId()).eq(WorkflowTask::getBusinessType,TYPE).eq(WorkflowTask::getBusinessId,application.getId())
                    .eq(WorkflowTask::getStatus,"WAITING").eq(WorkflowTask::getVersion,resume.getVersion()).set(WorkflowTask::getStatus,"PENDING")
                    .set(WorkflowTask::getDueAt,dueAt(runtime.plan().steps().get(snapshot.currentNodeIndex()).dueHours(),now)).set(WorkflowTask::getUpdatedAt,now).setSql("version=version+1")));
            recordTaskAction(actor,instance.getId(),resume.getId(),"ACTIVATE_ADD_SIGN","WAITING","PENDING",null,null,resume.getAssigneeUserId(),now);
        }
        if(!approved){
            var remaining=tasks.selectList(new LambdaQueryWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId,actor.tenantId()).eq(WorkflowTask::getInstanceId,instance.getId())
                    .eq(WorkflowTask::getBusinessType,TYPE).eq(WorkflowTask::getBusinessId,application.getId()).in(WorkflowTask::getStatus,"PENDING","WAITING").last("LIMIT 421"));
            if(remaining.size()>GenericApprovalTaskRuntime.MAX_TASKS)throw GenericApprovalPlan.unsupported();
            for(var outstanding:remaining){
                one(tasks.update(null,new LambdaUpdateWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId,actor.tenantId()).eq(WorkflowTask::getId,outstanding.getId())
                        .eq(WorkflowTask::getInstanceId,instance.getId()).eq(WorkflowTask::getStatus,outstanding.getStatus()).eq(WorkflowTask::getVersion,outstanding.getVersion())
                        .set(WorkflowTask::getStatus,"CANCELLED").set(WorkflowTask::getCompletedAt,now).set(WorkflowTask::getUpdatedAt,now).setSql("version=version+1")));
                recordTaskAction(actor,instance.getId(),outstanding.getId(),"CANCEL_ADD_SIGN",outstanding.getStatus(),"CANCELLED",null,null,null,now);
            }
        }
        one(applications.advanceExecution(actor.tenantId(), instance.getId(), snapshot.version(), advance ? snapshot.currentNodeIndex() + 1 : snapshot.currentNodeIndex()));
        one(instances.update(null, new LambdaUpdateWrapper<WorkflowInstance>().eq(WorkflowInstance::getId, instance.getId())
                .eq(WorkflowInstance::getTenantId, actor.tenantId()).eq(WorkflowInstance::getStatus, "RUNNING").eq(WorkflowInstance::getVersion, instance.getVersion())
                .set(WorkflowInstance::getStatus, ended ? "COMPLETED" : "RUNNING").set(WorkflowInstance::getCompletedAt, ended ? now : null)
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
        notifications.publishTransactional(actor.tenantId(), ended ? application.getApplicantUserId() : resume!=null ? resume.getAssigneeUserId() : next.assigneeUserId(), NotificationService.TYPE_APPROVAL,
                MessageUtils.resolve("genericApproval.notificationTitle"), MessageUtils.resolve(ended ? "genericApproval.notificationResult" : "genericApproval.notificationPending", snapshot.formName()),
                resume==null?"generic-approval":"generic-approval-task",resume==null?application.getId():resume.getId());
        return detail(userId, expectedTenantId, taskId, 1);
    }
    private ApprovalExecutionSnapshot snapshot(ResolvedUserAccess actor, WorkflowTask task, ApprovalApplication application) {
        ApprovalExecutionSnapshot snapshot = applications.selectExecution(actor.tenantId(), task.getInstanceId());
        if (snapshot == null || snapshot.formSchema() == null || snapshot.submittedAt() == null || application == null
                || !actor.tenantId().equals(snapshot.tenantId()) || !task.getInstanceId().equals(snapshot.instanceId())
                || !task.getBusinessId().equals(snapshot.applicationId()) || !application.getApplicantUserId().equals(snapshot.applicantUserId()))
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID, "validation.genericApproval.snapshotUnavailable");
        return snapshot;
    }

    private GenericApprovalPlan executable(WorkflowTask task, ApprovalExecutionSnapshot snapshot) {
        var runtime=runtime(task,snapshot);
        if(!runtime.active().getId().equals(task.getId()))throw GenericApprovalPlan.unsupported();
        return runtime.plan();
    }
    private GenericApprovalTaskRuntime.Context runtime(WorkflowTask task,ApprovalExecutionSnapshot snapshot){
        var plan=GenericApprovalPlan.decode(json,snapshot.planJson());
        var instanceTasks=tasks.selectList(new LambdaQueryWrapper<WorkflowTask>().eq(WorkflowTask::getTenantId,task.getTenantId())
                .eq(WorkflowTask::getInstanceId,task.getInstanceId()).eq(WorkflowTask::getBusinessType,TYPE).eq(WorkflowTask::getBusinessId,task.getBusinessId())
                .orderByAsc(WorkflowTask::getId).last("LIMIT 421"));
        var history=actions.selectList(new LambdaQueryWrapper<WorkflowActionLog>().eq(WorkflowActionLog::getTenantId,task.getTenantId())
                .eq(WorkflowActionLog::getInstanceId,task.getInstanceId()).in(WorkflowActionLog::getAction,"TRANSFER","ADD_SIGN_PRE","ADD_SIGN_POST")
                .orderByAsc(WorkflowActionLog::getId).last("LIMIT 14001"));
        return GenericApprovalTaskRuntime.resolve(plan,snapshot.currentNodeIndex(),new GenericApprovalTaskRuntime.Coordinates(
                task.getTenantId(),task.getInstanceId(),task.getBusinessId(),snapshot.applicantUserId()),instanceTasks,history);
    }
    private boolean writable(ResolvedUserAccess actor, WorkflowTask task, ApprovalApplication application, WorkflowInstance instance) {
        return application != null && instance != null && actor.permissions().contains("approval:act") && actor.userId().equals(task.getAssigneeUserId())
                && !actor.userId().equals(application.getApplicantUserId()) && actor.tenantId().equals(application.getTenantId())
                && actor.tenantId().equals(instance.getTenantId()) && TYPE.equals(instance.getBusinessType())
                && task.getBusinessId().equals(instance.getBusinessId()) && application.getApplicantUserId().equals(instance.getApplicantId())
                && (task.getParentTaskId()==null && task.getAddSignMode()==null || task.getParentTaskId()!=null && Set.of("PRE","POST").contains(task.getAddSignMode()==null?"":task.getAddSignMode()))
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
