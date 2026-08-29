package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.TraceContext;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalApplicationView;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.ApprovalDraftUpdateRequest;
import com.aiworkmate.dto.ApprovalSubmitRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.dto.WorkflowTimelineResponse;
import com.aiworkmate.entity.ApprovalApplication;
import com.aiworkmate.entity.ApprovalForm;
import com.aiworkmate.entity.ApprovalProcess;
import com.aiworkmate.entity.ApprovalRule;
import com.aiworkmate.entity.User;
import com.aiworkmate.entity.WorkflowActionLog;
import com.aiworkmate.entity.WorkflowInstance;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.ApprovalProcessMapper;
import com.aiworkmate.mapper.ApprovalRuleMapper;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowInstanceMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.GenericApprovalService;
import com.aiworkmate.service.NotificationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 通用审批提交服务实现。
 *
 * <p>安全与一致性边界：
 * <ul>
 *   <li>提交需 {@code route:approval-start} 权限（与发起审批页面可见权限一致），
 *       按认证 userId 实时解析角色；</li>
 *   <li>表单必须启用、未软删除，流程必须启用且绑定该表单，否则明确报错，
 *       禁止伪造提交成功；</li>
 *   <li>{@code formData} 按 {@code schema_json} 白名单校验：未知字段拒绝、
 *       必填缺失拒绝、类型（数字/日期/时间/文本）逐项校验并限制总长度；</li>
 *   <li>审批人按流程首节点解析：DIRECT_MANAGER 复用请假链路的
 *       「直属上级/部门默认审批人」SQL，USER 取指定用户，ROLE 取同角色最小 id；
 *       其余节点类型（SELF/MULTI_LEVEL/DEPARTMENT 等）当前不支持，直接返回
 *       能力不可用错误，不做静默降级；</li>
 *   <li>申请单、工作流实例、首个待办在同一事务内落库，并写入动作流水、
 *       业务审计并向受理人发送站内通知。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GenericApprovalServiceImpl implements GenericApprovalService {

    private static final String BUSINESS_TYPE = "GENERIC_APPROVAL";
    private static final int MAX_FIELD_VALUE_LENGTH = 500;
    private static final int MAX_LIST_ITEMS = 20;
    private static final int MAX_DATA_JSON_LENGTH = 20_000;
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d{2}:\\d{2}(:\\d{2})?$");
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private final ApprovalApplicationMapper applicationMapper;
    private final ApprovalFormMapper formMapper;
    private final ApprovalProcessMapper processMapper;
    private final ApprovalRuleMapper ruleMapper;
    private final LeaveApplicationMapper leaveMapper;
    private final UserMapper userMapper;
    private final WorkflowInstanceMapper instanceMapper;
    private final WorkflowTaskMapper taskMapper;
    private final WorkflowActionLogMapper actionLogMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Value("${app.workflow.approval-due-hours:48}")
    private long approvalDueHours;

    @Value("${app.workflow.reminder-interval-minutes:30}")
    private long reminderIntervalMinutes;

    @Override
    @Transactional
    public ApprovalApplicationResponse createDraft(Long userId, ApprovalDraftRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        ApprovalForm form = requireEnabledForm(actor, request.formKey(), "DRAFT_CREATE");
        ApprovalProcess process = resolveOptionalProcess(
                actor.tenantId(), form.getId(), request.processKey());
        Map<String, Object> formData = validateFormData(form, request.formData(), false);
        LocalDateTime now = LocalDateTime.now();

        ApprovalApplication application = new ApprovalApplication();
        application.setTenantId(actor.tenantId());
        application.setApplicantUserId(actor.userId());
        application.setFormId(form.getId());
        application.setProcessId(process == null ? null : process.getId());
        application.setFormKey(form.getFormKey());
        application.setFormName(form.getFormName());
        application.setTitle(form.getFormName());
        application.setDataJson(writeJson(formData));
        application.setStatus("DRAFT");
        application.setVersion(0);
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);

        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                application.getId().toString(), "DRAFT_CREATE", "SUCCESS",
                "保存通用表单草稿：" + form.getFormName());
        return response(actor, requireView(actor.tenantId(), application.getId()), null);
    }

    @Override
    @Transactional
    public ApprovalApplicationResponse updateDraft(Long userId,
                                                   Long id,
                                                   ApprovalDraftUpdateRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        ApprovalApplication application = requireOwnedApplication(actor, id);
        requireDraft(application);
        ApprovalForm form = requireEnabledForm(actor, application.getFormKey(), "DRAFT_UPDATE");
        Map<String, Object> formData = validateFormData(form, request.formData(), false);

        Long processId = application.getProcessId();
        if (request.processKey() != null) {
            ApprovalProcess process = resolveOptionalProcess(
                    actor.tenantId(), form.getId(), request.processKey());
            processId = process == null ? null : process.getId();
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getId, id)
                .eq(ApprovalApplication::getTenantId, actor.tenantId())
                .eq(ApprovalApplication::getApplicantUserId, actor.userId())
                .eq(ApprovalApplication::getStatus, "DRAFT")
                .eq(ApprovalApplication::getVersion, request.version())
                .set(ApprovalApplication::getProcessId, processId)
                .set(ApprovalApplication::getDataJson, writeJson(formData))
                .set(ApprovalApplication::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "DRAFT_UPDATE", "SUCCESS", "更新通用表单草稿");
        return response(actor, requireView(actor.tenantId(), id), null);
    }

    @Override
    @Transactional
    public ApprovalApplicationResponse submitDraft(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        ApprovalApplication application = requireOwnedApplication(actor, id);
        requireDraft(application);
        if (!request.version().equals(application.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        ApprovalForm form = requireEnabledForm(actor, application.getFormKey(), "DRAFT_SUBMIT");
        Map<String, Object> formData = validateFormData(form, readJson(application.getDataJson()), true);
        ApprovalProcess process = resolveEnabledProcess(
                actor.tenantId(), form.getId(), application.getProcessId());
        DefinitionSnapshot snapshot = createDefinitionSnapshot(actor.tenantId(), form, process);
        Long approverId = requireApprover(actor, form, process);
        Long definitionId = requireDefinition(actor.tenantId());
        LocalDateTime now = LocalDateTime.now();

        WorkflowInstance instance = createInstance(actor, application.getId(), definitionId, now);
        WorkflowTask task = createTask(actor, application.getId(), instance.getId(), approverId, now);
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getId, id)
                .eq(ApprovalApplication::getTenantId, actor.tenantId())
                .eq(ApprovalApplication::getApplicantUserId, actor.userId())
                .eq(ApprovalApplication::getStatus, "DRAFT")
                .eq(ApprovalApplication::getVersion, request.version())
                .set(ApprovalApplication::getProcessId, process.getId())
                .set(ApprovalApplication::getDataJson, writeJson(formData))
                .set(ApprovalApplication::getFormSchemaSnapshot, snapshot.formSchema())
                .set(ApprovalApplication::getFormVersionSnapshot, snapshot.formVersion())
                .set(ApprovalApplication::getProcessNodeSnapshot, snapshot.processNodes())
                .set(ApprovalApplication::getProcessVersionSnapshot, snapshot.processVersion())
                .set(ApprovalApplication::getRuleSnapshot, snapshot.rules())
                .set(ApprovalApplication::getStatus, "PENDING")
                .set(ApprovalApplication::getWorkflowInstanceId, instance.getId())
                .set(ApprovalApplication::getSubmittedAt, now)
                .set(ApprovalApplication::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        insertAction(actor, instance.getId(), task.getId(), "SUBMIT", "DRAFT", "PENDING", null);
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "SUBMIT", "SUCCESS", "提交通用表单草稿：" + form.getFormName());
        publishApprovalNotification(actor, approverId, form, id);
        return response(actor, requireView(actor.tenantId(), id), null);
    }

    @Override
    @Transactional
    public ApprovalApplicationResponse cancelDraft(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        ApprovalApplication application = requireOwnedApplication(actor, id);
        requireDraft(application);
        LocalDateTime now = LocalDateTime.now();
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getId, id)
                .eq(ApprovalApplication::getTenantId, actor.tenantId())
                .eq(ApprovalApplication::getApplicantUserId, actor.userId())
                .eq(ApprovalApplication::getStatus, "DRAFT")
                .eq(ApprovalApplication::getVersion, request.version())
                .set(ApprovalApplication::getStatus, "CANCELLED")
                .set(ApprovalApplication::getCompletedAt, now)
                .set(ApprovalApplication::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "DRAFT_CANCEL", "SUCCESS", "取消通用表单草稿");
        return response(actor, requireView(actor.tenantId(), id), null);
    }

    @Override
    @Transactional
    public ApprovalApplicationResponse withdraw(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        ApprovalApplication application = requireOwnedApplication(actor, id);
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.withdraw.stateInvalid");
        }
        if (!request.version().equals(application.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        WorkflowInstance instance = requireRunningInstance(actor, application);
        WorkflowTask task = requirePendingTask(actor, instance.getId(), application.getId());
        LocalDateTime now = LocalDateTime.now();

        int applicationUpdated = applicationMapper.update(null,
                new LambdaUpdateWrapper<ApprovalApplication>()
                        .eq(ApprovalApplication::getId, id)
                        .eq(ApprovalApplication::getTenantId, actor.tenantId())
                        .eq(ApprovalApplication::getApplicantUserId, actor.userId())
                        .eq(ApprovalApplication::getStatus, "PENDING")
                        .eq(ApprovalApplication::getVersion, request.version())
                        .set(ApprovalApplication::getStatus, "WITHDRAWN")
                        .set(ApprovalApplication::getCompletedAt, now)
                        .set(ApprovalApplication::getUpdatedAt, now)
                        .setSql("version = version + 1"));
        if (applicationUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        int taskUpdated = taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                .eq(WorkflowTask::getId, task.getId())
                .eq(WorkflowTask::getTenantId, actor.tenantId())
                .eq(WorkflowTask::getStatus, "PENDING")
                .eq(WorkflowTask::getVersion, task.getVersion())
                .set(WorkflowTask::getStatus, "CANCELLED")
                .set(WorkflowTask::getDecisionComment, "申请人撤回")
                .set(WorkflowTask::getCompletedAt, now)
                .set(WorkflowTask::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (taskUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        int instanceUpdated = instanceMapper.update(null,
                new LambdaUpdateWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getId, instance.getId())
                        .eq(WorkflowInstance::getTenantId, actor.tenantId())
                        .eq(WorkflowInstance::getStatus, "RUNNING")
                        .eq(WorkflowInstance::getVersion, instance.getVersion())
                        .set(WorkflowInstance::getStatus, "CANCELLED")
                        .set(WorkflowInstance::getCompletedAt, now)
                        .set(WorkflowInstance::getUpdatedAt, now)
                        .setSql("version = version + 1"));
        if (instanceUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }

        insertAction(actor, instance.getId(), task.getId(),
                "WITHDRAW", "PENDING", "WITHDRAWN", "申请人主动撤回");
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "WITHDRAW", "SUCCESS", "撤回通用审批申请");
        notificationService.publish(actor.tenantId(), task.getAssigneeUserId(),
                NotificationService.TYPE_APPROVAL,
                "审批申请已撤回",
                "申请人已撤回「" + application.getFormName() + "」，原待办已取消",
                "generic-approval", id);
        return response(actor, requireView(actor.tenantId(), id),
                actionLogMapper.selectBusinessTimeline(actor.tenantId(), BUSINESS_TYPE, id));
    }

    @Override
    @Transactional
    public ApprovalApplicationResponse remind(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        ApprovalApplication application = requireOwnedApplication(actor, id);
        if (!"PENDING".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.workflow.stateInvalid");
        }
        if (!request.version().equals(application.getVersion())) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        WorkflowInstance instance = requireRunningInstance(actor, application);
        WorkflowTask task = requirePendingTask(actor, instance.getId(), id);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusMinutes(Math.max(1, reminderIntervalMinutes));
        int taskUpdated = taskMapper.update(null, new LambdaUpdateWrapper<WorkflowTask>()
                .eq(WorkflowTask::getId, task.getId())
                .eq(WorkflowTask::getTenantId, actor.tenantId())
                .eq(WorkflowTask::getInstanceId, instance.getId())
                .eq(WorkflowTask::getBusinessType, BUSINESS_TYPE)
                .eq(WorkflowTask::getBusinessId, id)
                .eq(WorkflowTask::getStatus, "PENDING")
                .and(wrapper -> wrapper.isNull(WorkflowTask::getLastRemindedAt)
                        .or().le(WorkflowTask::getLastRemindedAt, threshold))
                .set(WorkflowTask::getLastRemindedAt, now)
                .set(WorkflowTask::getUpdatedAt, now)
                .setSql("reminder_count = reminder_count + 1"));
        if (taskUpdated != 1) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "validation.approval.remind.tooFrequent");
        }
        int applicationUpdated = applicationMapper.update(null,
                new LambdaUpdateWrapper<ApprovalApplication>()
                        .eq(ApprovalApplication::getId, id)
                        .eq(ApprovalApplication::getTenantId, actor.tenantId())
                        .eq(ApprovalApplication::getApplicantUserId, actor.userId())
                        .eq(ApprovalApplication::getStatus, "PENDING")
                        .eq(ApprovalApplication::getVersion, request.version())
                        .set(ApprovalApplication::getUpdatedAt, now)
                        .setSql("version = version + 1"));
        if (applicationUpdated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        insertAction(actor, instance.getId(), task.getId(),
                "REMIND", "PENDING", "PENDING", null);
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "REMIND", "SUCCESS", "催办通用审批待办");
        notificationService.publish(actor.tenantId(), task.getAssigneeUserId(),
                NotificationService.TYPE_APPROVAL,
                "审批催办提醒",
                "申请人提醒你及时处理「" + application.getFormName() + "」",
                "generic-approval", id);
        return response(actor, requireView(actor.tenantId(), id),
                actionLogMapper.selectBusinessTimeline(actor.tenantId(), BUSINESS_TYPE, id));
    }

    @Override
    @Transactional
    public ApprovalApplicationResponse reopen(Long userId, Long id, VersionRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        ApprovalApplication application = requireOwnedApplication(actor, id);
        String previousStatus = application.getStatus();
        if (!"REJECTED".equals(previousStatus) && !"WITHDRAWN".equals(previousStatus)) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.reopen.stateInvalid");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = applicationMapper.update(null, new LambdaUpdateWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getId, id)
                .eq(ApprovalApplication::getTenantId, actor.tenantId())
                .eq(ApprovalApplication::getApplicantUserId, actor.userId())
                .in(ApprovalApplication::getStatus, "REJECTED", "WITHDRAWN")
                .eq(ApprovalApplication::getVersion, request.version())
                .set(ApprovalApplication::getStatus, "DRAFT")
                .set(ApprovalApplication::getSubmittedAt, null)
                .set(ApprovalApplication::getCompletedAt, null)
                .set(ApprovalApplication::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        if (application.getWorkflowInstanceId() != null) {
            insertAction(actor, application.getWorkflowInstanceId(), null,
                    "REOPEN", previousStatus, "DRAFT", "恢复为草稿并准备重新提交");
        }
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                id.toString(), "REOPEN", "SUCCESS", "恢复通用审批申请为草稿");
        return response(actor, requireView(actor.tenantId(), id),
                actionLogMapper.selectBusinessTimeline(actor.tenantId(), BUSINESS_TYPE, id));
    }

    @Override
    @Transactional
    public ApprovalApplicationResponse submit(Long userId, ApprovalSubmitRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        String formKey = request.formKey().trim();
        ApprovalForm form = requireEnabledForm(actor, formKey, "SUBMIT");

        ApprovalProcess process = resolveProcess(actor.tenantId(), form.getId(), request.processKey());
        if (process == null) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    formKey, "SUBMIT", "DENIED", "表单未绑定可用流程");
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.processUnbound");
        }

        Map<String, Object> formData = validateFormData(form, request.formData(), true);
        Long approverId = resolveFirstApprover(actor, process);
        if (approverId == null) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    form.getId().toString(), "SUBMIT", "FAILURE", "未解析到有效审批人");
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED);
        }
        if (approverId.equals(actor.userId())) {
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED,
                    "validation.approval.submit.approverSelf");
        }
        Long definitionId = applicationMapper.selectGenericDefinitionId(actor.tenantId());
        if (definitionId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.definitionMissing");
        }

        LocalDateTime now = LocalDateTime.now();
        ApprovalApplication application = new ApprovalApplication();
        DefinitionSnapshot snapshot = createDefinitionSnapshot(actor.tenantId(), form, process);
        application.setTenantId(actor.tenantId());
        application.setApplicantUserId(actor.userId());
        application.setFormId(form.getId());
        application.setProcessId(process.getId());
        application.setFormKey(form.getFormKey());
        application.setFormName(form.getFormName());
        application.setTitle(form.getFormName());
        application.setDataJson(writeJson(formData));
        application.setFormSchemaSnapshot(snapshot.formSchema());
        application.setFormVersionSnapshot(snapshot.formVersion());
        application.setProcessNodeSnapshot(snapshot.processNodes());
        application.setProcessVersionSnapshot(snapshot.processVersion());
        application.setRuleSnapshot(snapshot.rules());
        application.setStatus("PENDING");
        application.setVersion(0);
        application.setSubmittedAt(now);
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);

        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantId(actor.tenantId());
        instance.setDefinitionId(definitionId);
        instance.setBusinessType(BUSINESS_TYPE);
        instance.setBusinessId(application.getId());
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
        task.setBusinessId(application.getId());
        task.setAssigneeUserId(approverId);
        task.setStatus("PENDING");
        task.setVersion(0);
        task.setDueAt(approvalDueHours > 0 ? now.plusHours(approvalDueHours) : null);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        applicationMapper.update(null, new LambdaUpdateWrapper<ApprovalApplication>()
                .eq(ApprovalApplication::getId, application.getId())
                .eq(ApprovalApplication::getTenantId, actor.tenantId())
                .set(ApprovalApplication::getWorkflowInstanceId, instance.getId())
                .set(ApprovalApplication::getUpdatedAt, now));
        insertAction(actor, instance.getId(), task.getId(), "SUBMIT", "NONE", "PENDING", null);
        auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                application.getId().toString(), "SUBMIT", "SUCCESS",
                "提交通用表单申请：" + form.getFormName());
        notificationService.publish(actor.tenantId(), approverId,
                NotificationService.TYPE_APPROVAL,
                "新的「" + form.getFormName() + "」待审批",
                "员工通过发起审批模板提交了申请，请及时处理",
                "generic-approval", application.getId());
        return response(actor, requireView(actor.tenantId(), application.getId()), null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalApplicationResponse> mine(Long userId, String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "route:approval-start");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        List<ApprovalApplicationResponse> records = applicationMapper.selectMine(
                        actor.tenantId(), actor.userId(), normalize(status), safeSize, offset)
                .stream().map(view -> response(actor, view, null)).toList();
        long total = applicationMapper.countMine(
                actor.tenantId(), actor.userId(), normalize(status));
        return PageResponse.of(records, total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalApplicationResponse detail(Long userId, Long id) {
        ResolvedUserAccess actor = requireAccess(userId);
        ApprovalApplicationView view = applicationMapper.selectView(actor.tenantId(), id);
        if (view == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        boolean applicant = actor.userId().equals(view.applicantUserId());
        boolean assignee = actor.userId().equals(view.taskAssigneeUserId()) && view.taskId() != null;
        boolean auditor = actor.permissions().contains("audit:read");
        if (!applicant && !assignee && !auditor) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    id.toString(), "READ", "DENIED", "资源归属校验失败");
            throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        List<WorkflowTimelineResponse> timeline = view.workflowInstanceId() == null
                ? null
                : actionLogMapper.selectBusinessTimeline(actor.tenantId(), BUSINESS_TYPE, view.id());
        return response(actor, view, timeline);
    }

    // ============================================================
    // 内部逻辑
    // ============================================================

    private ApprovalForm requireEnabledForm(ResolvedUserAccess actor,
                                            String rawFormKey,
                                            String action) {
        String formKey = rawFormKey == null ? "" : rawFormKey.trim();
        ApprovalForm form = formMapper.selectOne(new LambdaQueryWrapper<ApprovalForm>()
                .eq(ApprovalForm::getTenantId, actor.tenantId())
                .eq(ApprovalForm::getFormKey, formKey)
                .eq(ApprovalForm::getDeleted, false));
        if (form == null || !"ENABLED".equals(form.getStatus())) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    formKey, action, "DENIED", "表单不存在或未启用");
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.formUnavailable");
        }
        return form;
    }

    private ApprovalProcess resolveOptionalProcess(Long tenantId,
                                                   Long formId,
                                                   String processKey) {
        if (processKey == null || processKey.isBlank()) {
            return null;
        }
        ApprovalProcess process = resolveProcess(tenantId, formId, processKey);
        if (process == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.processUnbound");
        }
        return process;
    }

    private ApprovalProcess resolveEnabledProcess(Long tenantId, Long formId, Long processId) {
        ApprovalProcess process;
        if (processId == null) {
            process = resolveProcess(tenantId, formId, null);
        } else {
            process = processMapper.selectOne(new LambdaQueryWrapper<ApprovalProcess>()
                    .eq(ApprovalProcess::getId, processId)
                    .eq(ApprovalProcess::getTenantId, tenantId)
                    .eq(ApprovalProcess::getFormId, formId)
                    .eq(ApprovalProcess::getStatus, "ENABLED")
                    .eq(ApprovalProcess::getDeleted, false));
        }
        if (process == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.processUnbound");
        }
        return process;
    }

    private ApprovalApplication requireOwnedApplication(ResolvedUserAccess actor, Long id) {
        ApprovalApplication application = applicationMapper.selectOne(
                new LambdaQueryWrapper<ApprovalApplication>()
                        .eq(ApprovalApplication::getId, id)
                        .eq(ApprovalApplication::getTenantId, actor.tenantId())
                        .eq(ApprovalApplication::getApplicantUserId, actor.userId()));
        if (application == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return application;
    }

    private WorkflowInstance requireRunningInstance(ResolvedUserAccess actor,
                                                    ApprovalApplication application) {
        if (application.getWorkflowInstanceId() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.workflow.stateInvalid");
        }
        WorkflowInstance instance = instanceMapper.selectOne(
                new LambdaQueryWrapper<WorkflowInstance>()
                        .eq(WorkflowInstance::getId, application.getWorkflowInstanceId())
                        .eq(WorkflowInstance::getTenantId, actor.tenantId())
                        .eq(WorkflowInstance::getBusinessType, BUSINESS_TYPE)
                        .eq(WorkflowInstance::getBusinessId, application.getId())
                        .eq(WorkflowInstance::getApplicantId, actor.userId())
                        .eq(WorkflowInstance::getStatus, "RUNNING"));
        if (instance == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.workflow.stateInvalid");
        }
        return instance;
    }

    private WorkflowTask requirePendingTask(ResolvedUserAccess actor,
                                            Long instanceId,
                                            Long applicationId) {
        WorkflowTask task = taskMapper.selectOne(new LambdaQueryWrapper<WorkflowTask>()
                .eq(WorkflowTask::getTenantId, actor.tenantId())
                .eq(WorkflowTask::getInstanceId, instanceId)
                .eq(WorkflowTask::getBusinessType, BUSINESS_TYPE)
                .eq(WorkflowTask::getBusinessId, applicationId)
                .eq(WorkflowTask::getStatus, "PENDING"));
        if (task == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.workflow.stateInvalid");
        }
        return task;
    }

    private void requireDraft(ApprovalApplication application) {
        if (!"DRAFT".equals(application.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.draft.stateInvalid");
        }
    }

    private Long requireApprover(ResolvedUserAccess actor,
                                 ApprovalForm form,
                                 ApprovalProcess process) {
        Long approverId = resolveFirstApprover(actor, process);
        if (approverId == null) {
            auditService.record(actor.tenantId(), actor.userId(), BUSINESS_TYPE,
                    form.getId().toString(), "SUBMIT", "FAILURE", "未解析到有效审批人");
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED);
        }
        if (approverId.equals(actor.userId())) {
            throw new BusinessException(ErrorCode.APPROVER_NOT_CONFIGURED,
                    "validation.approval.submit.approverSelf");
        }
        return approverId;
    }

    private Long requireDefinition(Long tenantId) {
        Long definitionId = applicationMapper.selectGenericDefinitionId(tenantId);
        if (definitionId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.definitionMissing");
        }
        return definitionId;
    }

    private WorkflowInstance createInstance(ResolvedUserAccess actor,
                                            Long applicationId,
                                            Long definitionId,
                                            LocalDateTime now) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.setTenantId(actor.tenantId());
        instance.setDefinitionId(definitionId);
        instance.setBusinessType(BUSINESS_TYPE);
        instance.setBusinessId(applicationId);
        instance.setApplicantId(actor.userId());
        instance.setStatus("RUNNING");
        instance.setVersion(0);
        instance.setStartedAt(now);
        instance.setCreatedAt(now);
        instance.setUpdatedAt(now);
        instanceMapper.insert(instance);
        return instance;
    }

    private WorkflowTask createTask(ResolvedUserAccess actor,
                                    Long applicationId,
                                    Long instanceId,
                                    Long approverId,
                                    LocalDateTime now) {
        WorkflowTask task = new WorkflowTask();
        task.setTenantId(actor.tenantId());
        task.setInstanceId(instanceId);
        task.setBusinessType(BUSINESS_TYPE);
        task.setBusinessId(applicationId);
        task.setAssigneeUserId(approverId);
        task.setStatus("PENDING");
        task.setVersion(0);
        task.setDueAt(approvalDueHours > 0 ? now.plusHours(approvalDueHours) : null);
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return task;
    }

    private void publishApprovalNotification(ResolvedUserAccess actor,
                                             Long approverId,
                                             ApprovalForm form,
                                             Long applicationId) {
        notificationService.publish(actor.tenantId(), approverId,
                NotificationService.TYPE_APPROVAL,
                "新的「" + form.getFormName() + "」待审批",
                "员工通过发起审批模板提交了申请，请及时处理",
                "generic-approval", applicationId);
    }

    private ApprovalProcess resolveProcess(Long tenantId, Long formId, String processKey) {
        LambdaQueryWrapper<ApprovalProcess> q = new LambdaQueryWrapper<ApprovalProcess>()
                .eq(ApprovalProcess::getTenantId, tenantId)
                .eq(ApprovalProcess::getStatus, "ENABLED")
                .eq(ApprovalProcess::getDeleted, false)
                .eq(ApprovalProcess::getFormId, formId);
        if (processKey != null && !processKey.isBlank()) {
            q.eq(ApprovalProcess::getProcessKey, processKey.trim());
        }
        q.orderByAsc(ApprovalProcess::getId).last("LIMIT 1");
        return processMapper.selectOne(q);
    }

    /**
     * 提交瞬间生成不可变配置快照。规则按优先级和主键稳定排序，保证相同配置生成一致 JSON；
     * 草稿阶段不调用本方法，重新提交时则按当时的有效配置生成新快照。
     */
    private DefinitionSnapshot createDefinitionSnapshot(Long tenantId,
                                                        ApprovalForm form,
                                                        ApprovalProcess process) {
        List<Map<String, Object>> rules = ruleMapper.selectList(
                        new LambdaQueryWrapper<ApprovalRule>()
                                .eq(ApprovalRule::getTenantId, tenantId)
                                .eq(ApprovalRule::getStatus, "ENABLED")
                                .eq(ApprovalRule::getDeleted, false)
                                .orderByAsc(ApprovalRule::getPriority)
                                .orderByAsc(ApprovalRule::getId))
                .stream()
                .map(rule -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", rule.getId());
                    value.put("ruleKey", rule.getRuleKey());
                    value.put("ruleName", rule.getRuleName());
                    value.put("ruleType", rule.getRuleType());
                    value.put("priority", rule.getPriority());
                    value.put("conditionJson", rule.getConditionJson());
                    value.put("actionJson", rule.getActionJson());
                    value.put("version", rule.getVersion());
                    return value;
                }).toList();
        return new DefinitionSnapshot(
                form.getSchemaJson(), defaultVersion(form.getVersion()),
                process.getNodeJson(), defaultVersion(process.getVersion()),
                writeJson(rules));
    }

    private int defaultVersion(Integer version) {
        return version == null ? 0 : version;
    }

    /**
     * 按 schema_json 校验表单数据：白名单字段、必填、类型与长度限制。
     * 返回原样数据（仅结构校验，不改动业务取值），供统一序列化落库。
     */
    private Map<String, Object> validateFormData(ApprovalForm form,
                                                 Map<String, Object> formData,
                                                 boolean enforceRequired) {
        Map<String, FieldDef> defs = parseSchemaFields(form.getSchemaJson());
        for (String key : formData.keySet()) {
            if (!defs.containsKey(key)) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID,
                        "validation.approval.submit.unknownField");
            }
        }
        for (FieldDef def : defs.values()) {
            Object value = formData.get(def.name());
            if (value == null || (value instanceof String s && s.isBlank())) {
                if (enforceRequired && def.required()) {
                    throw new BusinessException(ErrorCode.REQUEST_INVALID,
                            "validation.approval.submit.fieldRequired");
                }
                continue;
            }
            checkValueType(def, value);
        }
        String serialized = writeJson(formData);
        if (serialized.length() > MAX_DATA_JSON_LENGTH) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.approval.submit.payloadTooLarge");
        }
        return formData;
    }

    private void checkValueType(FieldDef def, Object value) {
        String type = def.type() == null ? "text" : def.type();
        switch (type) {
            case "number", "amount" -> {
                if (!isNumeric(value)) {
                    throw new BusinessException(ErrorCode.REQUEST_INVALID,
                            "validation.approval.submit.valueInvalid");
                }
            }
            case "date" -> {
                String text = requireScalarText(def, value);
                if (!DATE_PATTERN.matcher(text).matches()) {
                    throw invalidValue();
                }
                try {
                    LocalDate.parse(text);
                } catch (DateTimeParseException e) {
                    throw invalidValue();
                }
            }
            case "time" -> {
                String text = requireScalarText(def, value);
                if (!TIME_PATTERN.matcher(text).matches()) {
                    throw invalidValue();
                }
            }
            default -> {
                if (value instanceof List<?> list) {
                    if (list.size() > MAX_LIST_ITEMS) {
                        throw invalidValue();
                    }
                    for (Object item : list) {
                        requireScalarText(def, item);
                    }
                } else {
                    requireScalarText(def, value);
                }
            }
        }
    }

    /** 仅接受字符串/数字/布尔标量，字符串长度受限；嵌套对象一律拒绝。 */
    private String requireScalarText(FieldDef def, Object value) {
        if (value instanceof String text) {
            if (text.length() > MAX_FIELD_VALUE_LENGTH) {
                throw invalidValue();
            }
            return text;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw invalidValue();
    }

    private boolean isNumeric(Object value) {
        if (value instanceof Number) {
            return true;
        }
        return value instanceof String text && NUMERIC_PATTERN.matcher(text).matches();
    }

    private BusinessException invalidValue() {
        return new BusinessException(ErrorCode.REQUEST_INVALID,
                "validation.approval.submit.valueInvalid");
    }

    private Map<String, FieldDef> parseSchemaFields(String schemaJson) {
        Map<String, FieldDef> defs = new LinkedHashMap<>();
        try {
            JsonNode root = objectMapper.readTree(schemaJson == null ? "{}" : schemaJson);
            JsonNode fields = root.path("fields");
            if (!fields.isArray()) {
                return defs;
            }
            for (JsonNode field : fields) {
                String name = field.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                defs.put(name, new FieldDef(
                        name,
                        field.path("label").asText(name),
                        field.path("type").asText("text"),
                        field.path("required").asBoolean(false)));
            }
        } catch (Exception e) {
            // schema 非法按空结构处理：无字段定义时仅做未知键拦截之外的宽松校验
            return defs;
        }
        return defs;
    }

    /** 解析流程首节点审批人。 */
    private Long resolveFirstApprover(ResolvedUserAccess actor, ApprovalProcess process) {
        JsonNode nodes;
        try {
            nodes = objectMapper.readTree(process.getNodeJson());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.processNodesInvalid");
        }
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.processNodesInvalid");
        }
        JsonNode first = nodes.get(0);
        String approveType = first.path("approveType").asText("DIRECT_MANAGER");
        String targetKey = first.path("targetKey").asText("");
        return switch (approveType) {
            case "DIRECT_MANAGER" -> leaveMapper.resolveApprover(actor.tenantId(), actor.userId());
            case "USER" -> resolveUserApprover(actor, targetKey);
            case "ROLE" -> targetKey.isBlank()
                    ? null
                    : applicationMapper.resolveRoleApprover(actor.tenantId(), targetKey.trim(), actor.userId());
            default -> throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.nodeTypeUnsupported");
        };
    }

    private Long resolveUserApprover(ResolvedUserAccess actor, String targetKey) {
        if (targetKey.isBlank()) {
            return null;
        }
        long approverId;
        try {
            approverId = Long.parseLong(targetKey.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.submit.nodeTargetInvalid");
        }
        User approver = userMapper.selectById(approverId);
        if (approver == null
                || !actor.tenantId().equals(approver.getTenantId())
                || approver.getStatus() == null
                || approver.getStatus() != 1) {
            return null;
        }
        return approver.getId();
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

    private ApprovalApplicationResponse response(ResolvedUserAccess actor,
                                                 ApprovalApplicationView view,
                                                 List<WorkflowTimelineResponse> timeline) {
        boolean overdue = "PENDING".equals(view.taskStatus())
                && view.taskDueAt() != null
                && view.taskDueAt().isBefore(LocalDateTime.now());
        boolean applicant = actor.userId().equals(view.applicantUserId());
        LocalDateTime remindAvailableAt = view.lastRemindedAt() == null
                ? null
                : view.lastRemindedAt().plusMinutes(Math.max(1, reminderIntervalMinutes));
        boolean canRemind = applicant
                && actor.permissions().contains("route:approval-start")
                && "PENDING".equals(view.status())
                && "PENDING".equals(view.taskStatus())
                && (remindAvailableAt == null || !LocalDateTime.now().isBefore(remindAvailableAt));
        return new ApprovalApplicationResponse(
                view.id(),
                view.applicantUserId(),
                view.applicantName(),
                view.formKey(),
                view.formName(),
                view.title(),
                view.dataJson(),
                view.formSchemaSnapshot(),
                view.formVersionSnapshot(),
                view.processNodeSnapshot(),
                view.processVersionSnapshot(),
                view.ruleSnapshot(),
                view.status(),
                view.version(),
                view.taskId(),
                view.taskVersion(),
                view.taskStatus(),
                view.taskDueAt(),
                overdue,
                view.reminderCount() == null ? 0 : view.reminderCount(),
                view.lastRemindedAt(),
                remindAvailableAt,
                canRemind,
                view.taskAssigneeUserId(),
                view.taskAssigneeName(),
                view.workflowStatus(),
                timeline == null ? null : List.copyOf(timeline),
                applicant && "PENDING".equals(view.status()),
                applicant && "DRAFT".equals(view.status()),
                applicant && "DRAFT".equals(view.status()),
                view.submittedAt(),
                view.completedAt(),
                view.createdAt(),
                view.updatedAt()
        );
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.approval.json.invalid");
        }
    }

    private Map<String, Object> readJson(String dataJson) {
        try {
            if (dataJson == null || dataJson.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(dataJson, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.approval.json.invalid");
        }
    }

    private ApprovalApplicationView requireView(Long tenantId, Long id) {
        ApprovalApplicationView view = applicationMapper.selectView(tenantId, id);
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

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record FieldDef(String name, String label, String type, boolean required) {
    }

    private record DefinitionSnapshot(String formSchema,
                                      int formVersion,
                                      String processNodes,
                                      int processVersion,
                                      String rules) {
    }
}
