package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.ApprovalFormRequest;
import com.aiworkmate.dto.ApprovalFormResponse;
import com.aiworkmate.dto.ApprovalProcessRequest;
import com.aiworkmate.dto.ApprovalProcessResponse;
import com.aiworkmate.dto.ApprovalRuleRequest;
import com.aiworkmate.dto.ApprovalRuleResponse;
import com.aiworkmate.entity.ApprovalForm;
import com.aiworkmate.entity.ApprovalProcess;
import com.aiworkmate.entity.ApprovalRule;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.ApprovalProcessMapper;
import com.aiworkmate.mapper.ApprovalRuleMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.ApprovalEngineService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 审批引擎配置服务实现。
 *
 * <p>表单定义 / 流程定义 / 审批规则均为租户级配置 CRUD：
 * <ul>
 *   <li>软删除（{@code deleted = true}），键重复创建时自动复活软删除行，保证键始终可用；</li>
 *   <li>乐观锁：更新必须携带当前 {@code version}，冲突抛 {@link ErrorCode#VERSION_CONFLICT}；</li>
 *   <li>JSON 字段由应用层校验结构（表单/规则必须为 JSON 对象，流程节点必须为 JSON 数组）；</li>
 *   <li>读取需 {@code approval:read}，写入需 {@code approval:manage}，按认证 userId 实时解析。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ApprovalEngineServiceImpl implements ApprovalEngineService {

    private static final String RESOURCE_FORM = "APPROVAL_FORM";
    private static final String RESOURCE_PROCESS = "APPROVAL_PROCESS";
    private static final String RESOURCE_RULE = "APPROVAL_RULE";

    private final ApprovalFormMapper formMapper;
    private final ApprovalProcessMapper processMapper;
    private final ApprovalRuleMapper ruleMapper;
    private final UserMapper userMapper;
    private final UserAccessService userAccessService;
    private final BusinessAuditService auditService;
    private final ObjectMapper objectMapper;

    // ============================================================
    // 表单定义
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalFormResponse> listForms(Long userId, String keyword, String status,
                                                        int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        String kw = normalize(keyword);

        LambdaQueryWrapper<ApprovalForm> q = baseFormQuery(actor.tenantId(), kw, status)
                .orderByDesc(ApprovalForm::getUpdatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<ApprovalForm> rows = formMapper.selectList(q);
        long total = formMapper.selectCount(baseFormQuery(actor.tenantId(), kw, status));

        boolean canManage = actor.permissions().contains("approval:manage");
        Map<Long, String> creatorNames = batchCreatorNames(rows.stream()
                .map(ApprovalForm::getCreatedBy)
                .filter(java.util.Objects::nonNull)
                .toList());
        return PageResponse.of(rows.stream()
                .map(f -> toFormResponse(f, nameOf(creatorNames, f.getCreatedBy()), canManage))
                .toList(), total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalFormResponse getForm(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        ApprovalForm form = requireForm(actor.tenantId(), id);
        return toFormResponse(form, singleUserName(form.getCreatedBy()),
                actor.permissions().contains("approval:manage"));
    }

    @Override
    @Transactional
    public ApprovalFormResponse createForm(Long userId, ApprovalFormRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        String schemaJson = requireJsonObject(request.schemaJson());
        String formKey = request.formKey().trim();
        LocalDateTime now = LocalDateTime.now();

        ApprovalForm existing = formMapper.selectOne(new LambdaQueryWrapper<ApprovalForm>()
                .eq(ApprovalForm::getTenantId, actor.tenantId())
                .eq(ApprovalForm::getFormKey, formKey));
        if (existing != null && !Boolean.TRUE.equals(existing.getDeleted())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.key.duplicate");
        }
        if (existing != null) {
            // 复活软删除行，保证键名可复用
            existing.setFormKey(formKey);
            existing.setFormName(request.formName().trim());
            existing.setDescription(trim(request.description()));
            existing.setSchemaJson(schemaJson);
            existing.setStatus(defaultStatus(request.status()));
            existing.setCreatedBy(actor.userId());
            existing.setDeleted(false);
            existing.setUpdatedAt(now);
            existing.setVersion(existing.getVersion() + 1);
            formMapper.updateById(existing);
            auditService.record(actor.tenantId(), actor.userId(), RESOURCE_FORM,
                    existing.getId().toString(), "CREATE", "SUCCESS", "重建审批表单定义");
            return toFormResponse(existing, singleUserName(actor.userId()), true);
        }

        ApprovalForm form = new ApprovalForm();
        form.setTenantId(actor.tenantId());
        form.setFormKey(formKey);
        form.setFormName(request.formName().trim());
        form.setDescription(trim(request.description()));
        form.setSchemaJson(schemaJson);
        form.setStatus(defaultStatus(request.status()));
        form.setVersion(1);
        form.setCreatedBy(actor.userId());
        form.setCreatedAt(now);
        form.setUpdatedAt(now);
        form.setDeleted(false);
        formMapper.insert(form);
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_FORM,
                form.getId().toString(), "CREATE", "SUCCESS", "新增审批表单定义");
        return toFormResponse(form, singleUserName(actor.userId()), true);
    }

    @Override
    @Transactional
    public ApprovalFormResponse updateForm(Long userId, Long id, ApprovalFormRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        requireVersion(request.version());
        ApprovalForm form = requireForm(actor.tenantId(), id);
        String schemaJson = requireJsonObject(request.schemaJson());
        requireKeyFree(formMapper.selectCount(new LambdaQueryWrapper<ApprovalForm>()
                .eq(ApprovalForm::getTenantId, actor.tenantId())
                .eq(ApprovalForm::getFormKey, request.formKey().trim())
                .ne(ApprovalForm::getId, id)) > 0);

        LocalDateTime now = LocalDateTime.now();
        int updated = formMapper.update(null, new LambdaUpdateWrapper<ApprovalForm>()
                .eq(ApprovalForm::getId, id)
                .eq(ApprovalForm::getTenantId, actor.tenantId())
                .eq(ApprovalForm::getDeleted, false)
                .eq(ApprovalForm::getVersion, request.version())
                .set(ApprovalForm::getFormKey, request.formKey().trim())
                .set(ApprovalForm::getFormName, request.formName().trim())
                .set(ApprovalForm::getDescription, trim(request.description()))
                .set(ApprovalForm::getSchemaJson, schemaJson)
                .set(ApprovalForm::getStatus, defaultStatus(request.status()))
                .set(ApprovalForm::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_FORM,
                id.toString(), "UPDATE", "SUCCESS", "更新审批表单定义");
        return toFormResponse(formMapper.selectById(id), singleUserName(form.getCreatedBy()), true);
    }

    @Override
    @Transactional
    public void deleteForm(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        int updated = formMapper.update(null, new LambdaUpdateWrapper<ApprovalForm>()
                .eq(ApprovalForm::getId, id)
                .eq(ApprovalForm::getTenantId, actor.tenantId())
                .eq(ApprovalForm::getDeleted, false)
                .set(ApprovalForm::getDeleted, true)
                .set(ApprovalForm::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_FORM,
                id.toString(), "DELETE", "SUCCESS", "删除审批表单定义");
    }

    // ============================================================
    // 流程定义
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalProcessResponse> listProcesses(Long userId, String keyword,
                                                               String status, int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        String kw = normalize(keyword);

        LambdaQueryWrapper<ApprovalProcess> q = baseProcessQuery(actor.tenantId(), kw, status)
                .orderByDesc(ApprovalProcess::getUpdatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<ApprovalProcess> rows = processMapper.selectList(q);
        long total = processMapper.selectCount(baseProcessQuery(actor.tenantId(), kw, status));

        boolean canManage = actor.permissions().contains("approval:manage");
        Map<Long, String> formNames = formNames(rows.stream()
                .map(ApprovalProcess::getFormId)
                .filter(java.util.Objects::nonNull)
                .toList());
        Map<Long, String> creatorNames = batchCreatorNames(rows.stream()
                .map(ApprovalProcess::getCreatedBy)
                .filter(java.util.Objects::nonNull)
                .toList());
        return PageResponse.of(rows.stream()
                .map(p -> toProcessResponse(p, nameOf(formNames, p.getFormId()),
                        nameOf(creatorNames, p.getCreatedBy()), canManage))
                .toList(), total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalProcessResponse getProcess(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        ApprovalProcess process = requireProcess(actor.tenantId(), id);
        return toProcessResponse(process, formNameOf(actor.tenantId(), process.getFormId()),
                singleUserName(process.getCreatedBy()),
                actor.permissions().contains("approval:manage"));
    }

    @Override
    @Transactional
    public ApprovalProcessResponse createProcess(Long userId, ApprovalProcessRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        String nodeJson = requireJsonArray(request.nodeJson());
        Long formId = requireFormRef(actor.tenantId(), request.formId());
        String processKey = request.processKey().trim();
        LocalDateTime now = LocalDateTime.now();

        ApprovalProcess existing = processMapper.selectOne(new LambdaQueryWrapper<ApprovalProcess>()
                .eq(ApprovalProcess::getTenantId, actor.tenantId())
                .eq(ApprovalProcess::getProcessKey, processKey));
        if (existing != null && !Boolean.TRUE.equals(existing.getDeleted())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.key.duplicate");
        }
        if (existing != null) {
            existing.setProcessKey(processKey);
            existing.setProcessName(request.processName().trim());
            existing.setDescription(trim(request.description()));
            existing.setFormId(formId);
            existing.setNodeJson(nodeJson);
            existing.setStatus(defaultStatus(request.status()));
            existing.setCreatedBy(actor.userId());
            existing.setDeleted(false);
            existing.setUpdatedAt(now);
            existing.setVersion(existing.getVersion() + 1);
            processMapper.updateById(existing);
            auditService.record(actor.tenantId(), actor.userId(), RESOURCE_PROCESS,
                    existing.getId().toString(), "CREATE", "SUCCESS", "重建审批流程定义");
            return toProcessResponse(existing, formNameOf(actor.tenantId(), formId),
                    singleUserName(actor.userId()), true);
        }

        ApprovalProcess process = new ApprovalProcess();
        process.setTenantId(actor.tenantId());
        process.setProcessKey(processKey);
        process.setProcessName(request.processName().trim());
        process.setDescription(trim(request.description()));
        process.setFormId(formId);
        process.setNodeJson(nodeJson);
        process.setStatus(defaultStatus(request.status()));
        process.setVersion(1);
        process.setCreatedBy(actor.userId());
        process.setCreatedAt(now);
        process.setUpdatedAt(now);
        process.setDeleted(false);
        processMapper.insert(process);
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_PROCESS,
                process.getId().toString(), "CREATE", "SUCCESS", "新增审批流程定义");
        return toProcessResponse(process, formNameOf(actor.tenantId(), formId),
                singleUserName(actor.userId()), true);
    }

    @Override
    @Transactional
    public ApprovalProcessResponse updateProcess(Long userId, Long id,
                                                 ApprovalProcessRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        requireVersion(request.version());
        ApprovalProcess process = requireProcess(actor.tenantId(), id);
        String nodeJson = requireJsonArray(request.nodeJson());
        Long formId = requireFormRef(actor.tenantId(), request.formId());
        requireKeyFree(processMapper.selectCount(new LambdaQueryWrapper<ApprovalProcess>()
                .eq(ApprovalProcess::getTenantId, actor.tenantId())
                .eq(ApprovalProcess::getProcessKey, request.processKey().trim())
                .ne(ApprovalProcess::getId, id)) > 0);

        LocalDateTime now = LocalDateTime.now();
        int updated = processMapper.update(null, new LambdaUpdateWrapper<ApprovalProcess>()
                .eq(ApprovalProcess::getId, id)
                .eq(ApprovalProcess::getTenantId, actor.tenantId())
                .eq(ApprovalProcess::getDeleted, false)
                .eq(ApprovalProcess::getVersion, request.version())
                .set(ApprovalProcess::getProcessKey, request.processKey().trim())
                .set(ApprovalProcess::getProcessName, request.processName().trim())
                .set(ApprovalProcess::getDescription, trim(request.description()))
                .set(ApprovalProcess::getFormId, formId)
                .set(ApprovalProcess::getNodeJson, nodeJson)
                .set(ApprovalProcess::getStatus, defaultStatus(request.status()))
                .set(ApprovalProcess::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_PROCESS,
                id.toString(), "UPDATE", "SUCCESS", "更新审批流程定义");
        return toProcessResponse(processMapper.selectById(id),
                formNameOf(actor.tenantId(), formId),
                singleUserName(process.getCreatedBy()), true);
    }

    @Override
    @Transactional
    public void deleteProcess(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        int updated = processMapper.update(null, new LambdaUpdateWrapper<ApprovalProcess>()
                .eq(ApprovalProcess::getId, id)
                .eq(ApprovalProcess::getTenantId, actor.tenantId())
                .eq(ApprovalProcess::getDeleted, false)
                .set(ApprovalProcess::getDeleted, true)
                .set(ApprovalProcess::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_PROCESS,
                id.toString(), "DELETE", "SUCCESS", "删除审批流程定义");
    }

    // ============================================================
    // 审批规则
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ApprovalRuleResponse> listRules(Long userId, String keyword, String status,
                                                        int page, int size) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;
        String kw = normalize(keyword);

        LambdaQueryWrapper<ApprovalRule> q = baseRuleQuery(actor.tenantId(), kw, status)
                .orderByAsc(ApprovalRule::getPriority)
                .orderByDesc(ApprovalRule::getUpdatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + offset);
        List<ApprovalRule> rows = ruleMapper.selectList(q);
        long total = ruleMapper.selectCount(baseRuleQuery(actor.tenantId(), kw, status));

        boolean canManage = actor.permissions().contains("approval:manage");
        Map<Long, String> creatorNames = batchCreatorNames(rows.stream()
                .map(ApprovalRule::getCreatedBy)
                .filter(java.util.Objects::nonNull)
                .toList());
        return PageResponse.of(rows.stream()
                .map(r -> toRuleResponse(r, nameOf(creatorNames, r.getCreatedBy()), canManage))
                .toList(), total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalRuleResponse getRule(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:read");
        ApprovalRule rule = requireRule(actor.tenantId(), id);
        return toRuleResponse(rule, singleUserName(rule.getCreatedBy()),
                actor.permissions().contains("approval:manage"));
    }

    @Override
    @Transactional
    public ApprovalRuleResponse createRule(Long userId, ApprovalRuleRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        String conditionJson = requireJsonObject(request.conditionJson());
        String actionJson = requireJsonObject(request.actionJson());
        String ruleKey = request.ruleKey().trim();
        LocalDateTime now = LocalDateTime.now();

        ApprovalRule existing = ruleMapper.selectOne(new LambdaQueryWrapper<ApprovalRule>()
                .eq(ApprovalRule::getTenantId, actor.tenantId())
                .eq(ApprovalRule::getRuleKey, ruleKey));
        if (existing != null && !Boolean.TRUE.equals(existing.getDeleted())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.key.duplicate");
        }
        if (existing != null) {
            existing.setRuleKey(ruleKey);
            existing.setRuleName(request.ruleName().trim());
            existing.setRuleType(request.ruleType());
            existing.setPriority(defaultPriority(request.priority()));
            existing.setConditionJson(conditionJson);
            existing.setActionJson(actionJson);
            existing.setDescription(trim(request.description()));
            existing.setStatus(defaultStatus(request.status()));
            existing.setCreatedBy(actor.userId());
            existing.setDeleted(false);
            existing.setUpdatedAt(now);
            existing.setVersion(existing.getVersion() + 1);
            ruleMapper.updateById(existing);
            auditService.record(actor.tenantId(), actor.userId(), RESOURCE_RULE,
                    existing.getId().toString(), "CREATE", "SUCCESS", "重建审批规则");
            return toRuleResponse(existing, singleUserName(actor.userId()), true);
        }

        ApprovalRule rule = new ApprovalRule();
        rule.setTenantId(actor.tenantId());
        rule.setRuleKey(ruleKey);
        rule.setRuleName(request.ruleName().trim());
        rule.setRuleType(request.ruleType());
        rule.setPriority(defaultPriority(request.priority()));
        rule.setConditionJson(conditionJson);
        rule.setActionJson(actionJson);
        rule.setDescription(trim(request.description()));
        rule.setStatus(defaultStatus(request.status()));
        rule.setVersion(1);
        rule.setCreatedBy(actor.userId());
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);
        rule.setDeleted(false);
        ruleMapper.insert(rule);
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_RULE,
                rule.getId().toString(), "CREATE", "SUCCESS", "新增审批规则");
        return toRuleResponse(rule, singleUserName(actor.userId()), true);
    }

    @Override
    @Transactional
    public ApprovalRuleResponse updateRule(Long userId, Long id, ApprovalRuleRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        requireVersion(request.version());
        ApprovalRule rule = requireRule(actor.tenantId(), id);
        String conditionJson = requireJsonObject(request.conditionJson());
        String actionJson = requireJsonObject(request.actionJson());
        requireKeyFree(ruleMapper.selectCount(new LambdaQueryWrapper<ApprovalRule>()
                .eq(ApprovalRule::getTenantId, actor.tenantId())
                .eq(ApprovalRule::getRuleKey, request.ruleKey().trim())
                .ne(ApprovalRule::getId, id)) > 0);

        LocalDateTime now = LocalDateTime.now();
        int updated = ruleMapper.update(null, new LambdaUpdateWrapper<ApprovalRule>()
                .eq(ApprovalRule::getId, id)
                .eq(ApprovalRule::getTenantId, actor.tenantId())
                .eq(ApprovalRule::getDeleted, false)
                .eq(ApprovalRule::getVersion, request.version())
                .set(ApprovalRule::getRuleKey, request.ruleKey().trim())
                .set(ApprovalRule::getRuleName, request.ruleName().trim())
                .set(ApprovalRule::getRuleType, request.ruleType())
                .set(ApprovalRule::getPriority, defaultPriority(request.priority()))
                .set(ApprovalRule::getConditionJson, conditionJson)
                .set(ApprovalRule::getActionJson, actionJson)
                .set(ApprovalRule::getDescription, trim(request.description()))
                .set(ApprovalRule::getStatus, defaultStatus(request.status()))
                .set(ApprovalRule::getUpdatedAt, now)
                .setSql("version = version + 1"));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT);
        }
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_RULE,
                id.toString(), "UPDATE", "SUCCESS", "更新审批规则");
        return toRuleResponse(ruleMapper.selectById(id), singleUserName(rule.getCreatedBy()), true);
    }

    @Override
    @Transactional
    public void deleteRule(Long userId, Long id) {
        ResolvedUserAccess actor = requirePermission(userId, "approval:manage");
        int updated = ruleMapper.update(null, new LambdaUpdateWrapper<ApprovalRule>()
                .eq(ApprovalRule::getId, id)
                .eq(ApprovalRule::getTenantId, actor.tenantId())
                .eq(ApprovalRule::getDeleted, false)
                .set(ApprovalRule::getDeleted, true)
                .set(ApprovalRule::getUpdatedAt, LocalDateTime.now()));
        if (updated != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        auditService.record(actor.tenantId(), actor.userId(), RESOURCE_RULE,
                id.toString(), "DELETE", "SUCCESS", "删除审批规则");
    }

    // ============================================================
    // 私有辅助
    // ============================================================

    private ResolvedUserAccess requirePermission(Long userId, String permission) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null || !access.permissions().contains(permission)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        return access;
    }

    private void requireVersion(Integer version) {
        if (version == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.approval.version.required");
        }
    }

    /** 校验 JSON 结构：表单 schema 与规则条件/动作必须为 JSON 对象，流程节点必须为 JSON 数组。 */
    private String requireJsonObject(String text) {
        return requireJson(text, true);
    }

    private String requireJsonArray(String text) {
        return requireJson(text, false);
    }

    private String requireJson(String text, boolean mustBeObject) {
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node == null || (mustBeObject ? !node.isObject() : !node.isArray())) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID,
                        "validation.approval.json.invalid");
            }
            return node.toString();
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID,
                    "validation.approval.json.invalid");
        }
    }

    private ApprovalForm requireForm(Long tenantId, Long id) {
        ApprovalForm form = formMapper.selectById(id);
        if (form == null || !tenantId.equals(form.getTenantId())
                || Boolean.TRUE.equals(form.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return form;
    }

    /** 流程关联表单：formId 为空允许（流程暂不绑定表单），否则必须是本租户未删除的表单。 */
    private Long requireFormRef(Long tenantId, Long formId) {
        if (formId == null) {
            return null;
        }
        requireForm(tenantId, formId);
        return formId;
    }

    private ApprovalProcess requireProcess(Long tenantId, Long id) {
        ApprovalProcess process = processMapper.selectById(id);
        if (process == null || !tenantId.equals(process.getTenantId())
                || Boolean.TRUE.equals(process.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return process;
    }

    private ApprovalRule requireRule(Long tenantId, Long id) {
        ApprovalRule rule = ruleMapper.selectById(id);
        if (rule == null || !tenantId.equals(rule.getTenantId())
                || Boolean.TRUE.equals(rule.getDeleted())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return rule;
    }

    private void requireKeyFree(boolean duplicated) {
        if (duplicated) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID,
                    "validation.approval.key.duplicate");
        }
    }

    private LambdaQueryWrapper<ApprovalForm> baseFormQuery(Long tenantId, String kw, String status) {
        LambdaQueryWrapper<ApprovalForm> q = new LambdaQueryWrapper<ApprovalForm>()
                .eq(ApprovalForm::getTenantId, tenantId)
                .eq(ApprovalForm::getDeleted, false);
        if (kw != null) {
            q.and(w -> w.like(ApprovalForm::getFormKey, kw)
                    .or().like(ApprovalForm::getFormName, kw));
        }
        if (status != null && !status.isBlank()) {
            q.eq(ApprovalForm::getStatus, status.trim());
        }
        return q;
    }

    private LambdaQueryWrapper<ApprovalProcess> baseProcessQuery(Long tenantId, String kw,
                                                                 String status) {
        LambdaQueryWrapper<ApprovalProcess> q = new LambdaQueryWrapper<ApprovalProcess>()
                .eq(ApprovalProcess::getTenantId, tenantId)
                .eq(ApprovalProcess::getDeleted, false);
        if (kw != null) {
            q.and(w -> w.like(ApprovalProcess::getProcessKey, kw)
                    .or().like(ApprovalProcess::getProcessName, kw));
        }
        if (status != null && !status.isBlank()) {
            q.eq(ApprovalProcess::getStatus, status.trim());
        }
        return q;
    }

    private LambdaQueryWrapper<ApprovalRule> baseRuleQuery(Long tenantId, String kw, String status) {
        LambdaQueryWrapper<ApprovalRule> q = new LambdaQueryWrapper<ApprovalRule>()
                .eq(ApprovalRule::getTenantId, tenantId)
                .eq(ApprovalRule::getDeleted, false);
        if (kw != null) {
            q.and(w -> w.like(ApprovalRule::getRuleKey, kw)
                    .or().like(ApprovalRule::getRuleName, kw));
        }
        if (status != null && !status.isBlank()) {
            q.eq(ApprovalRule::getStatus, status.trim());
        }
        return q;
    }

    private Map<Long, String> formNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<ApprovalForm> forms = formMapper.selectBatchIds(ids);
        return forms.stream()
                .filter(f -> !Boolean.TRUE.equals(f.getDeleted()))
                .collect(Collectors.toMap(ApprovalForm::getId, ApprovalForm::getFormName,
                        (a, b) -> a));
    }

    private String formNameOf(Long tenantId, Long formId) {
        if (formId == null) {
            return null;
        }
        ApprovalForm form = formMapper.selectById(formId);
        return form == null || Boolean.TRUE.equals(form.getDeleted()) ? null : form.getFormName();
    }

    private Map<Long, String> batchCreatorNames(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(ids);
        return users.stream().collect(Collectors.toMap(User::getId,
                u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                        ? u.getDisplayName() : u.getUsername(),
                (a, b) -> a));
    }

    /** 空键安全查询：种子数据 createdBy/formId 可为 NULL，且不可变 Map 禁止 get(null)。 */
    private String nameOf(Map<Long, String> names, Long id) {
        return id == null ? null : names.get(id);
    }

    private String singleUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getUsername();
    }

    private String defaultStatus(String status) {
        return status == null || status.isBlank() ? "ENABLED" : status.trim();
    }

    private Integer defaultPriority(Integer priority) {
        return priority == null ? 100 : priority;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private ApprovalFormResponse toFormResponse(ApprovalForm form, String creatorName,
                                                boolean canManage) {
        return new ApprovalFormResponse(
                form.getId(), form.getFormKey(), form.getFormName(), form.getDescription(),
                form.getSchemaJson(), form.getStatus(), form.getVersion(), creatorName,
                form.getCreatedAt(), form.getUpdatedAt(), canManage, canManage);
    }

    private ApprovalProcessResponse toProcessResponse(ApprovalProcess process, String formName,
                                                      String creatorName, boolean canManage) {
        return new ApprovalProcessResponse(
                process.getId(), process.getProcessKey(), process.getProcessName(),
                process.getDescription(), process.getFormId(), formName, process.getNodeJson(),
                process.getStatus(), process.getVersion(), creatorName,
                process.getCreatedAt(), process.getUpdatedAt(), canManage, canManage);
    }

    private ApprovalRuleResponse toRuleResponse(ApprovalRule rule, String creatorName,
                                                boolean canManage) {
        return new ApprovalRuleResponse(
                rule.getId(), rule.getRuleKey(), rule.getRuleName(), rule.getRuleType(),
                rule.getPriority(), rule.getConditionJson(), rule.getActionJson(),
                rule.getDescription(), rule.getStatus(), rule.getVersion(), creatorName,
                rule.getCreatedAt(), rule.getUpdatedAt(), canManage, canManage);
    }
}