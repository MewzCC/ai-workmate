package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.UploadProperties;
import com.aiworkmate.dto.ApprovalApplicationAttachmentResponse;
import com.aiworkmate.entity.ApprovalApplication;
import com.aiworkmate.entity.ApprovalApplicationAttachment;
import com.aiworkmate.entity.WorkflowTask;
import com.aiworkmate.mapper.ApprovalApplicationAttachmentMapper;
import com.aiworkmate.mapper.ApprovalApplicationMapper;
import com.aiworkmate.mapper.ApprovalFormMapper;
import com.aiworkmate.mapper.WorkflowActionLogMapper;
import com.aiworkmate.mapper.WorkflowTaskMapper;
import com.aiworkmate.service.ApprovalApplicationAttachmentService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.FileParserService;
import com.aiworkmate.service.ObjectStorageService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.AttachmentContent;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalApplicationAttachmentServiceImpl implements ApprovalApplicationAttachmentService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final ApprovalApplicationAttachmentMapper attachments;
    private final ApprovalApplicationMapper applications;
    private final ApprovalFormMapper forms;
    private final WorkflowTaskMapper tasks;
    private final WorkflowActionLogMapper actions;
    private final UserAccessService access;
    private final FileParserService parser;
    private final ObjectStorageService storage;
    private final UploadProperties limits;
    private final BusinessAuditService audit;
    private final ObjectMapper json;

    @Override
    @Transactional
    public ApprovalApplicationAttachmentResponse upload(Long userId, Long applicationId, String fieldName, MultipartFile file) {
        var actor = actor(userId);
        if (!actor.permissions().containsAll(Set.of("route:approval-start", "approval:create")))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        requirePositive(applicationId);
        var application = applications.lockApplication(actor.tenantId(), applicationId);
        actor = actor(userId);
        if (!actor.permissions().containsAll(Set.of("route:approval-start", "approval:create")))
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        if (application == null || !actor.tenantId().equals(application.getTenantId())
                || !actor.userId().equals(application.getApplicantUserId()))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!"DRAFT".equals(application.getStatus())) throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        var form = forms.selectById(application.getFormId());
        if (form == null || !actor.tenantId().equals(form.getTenantId()) || Boolean.TRUE.equals(form.getDeleted())
                || !"ENABLED".equals(form.getStatus())) throw invalid();
        String fieldType = fieldType(form.getSchemaJson(), fieldName);
        if (file == null || file.isEmpty() || file.getSize() > limits.getFileMaxBytes()) throw invalid();
        String name = displayName(file);
        Path temporary = null;
        String key = "approval-attachments/" + actor.tenantId() + "/" + applicationId + "/" + UUID.randomUUID();
        try {
            temporary = Files.createTempFile("approval-upload-", ".bin");
            file.transferTo(temporary);
            var parsed = parser.inspect(temporary, name, actor.userId());
            if (parsed.image() && file.getSize() > limits.getImageMaxBytes()
                    || "image".equals(fieldType) && !IMAGE_TYPES.contains(parsed.mimeType())) throw invalid();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try { storage.delete(key); }
                        catch (RuntimeException cleanupFailure) { log.warn("Unable to remove rolled-back application attachment"); }
                    }
                }
            });
            try (var stream = Files.newInputStream(temporary)) {
                storage.store(key, stream, file.getSize(), parsed.mimeType());
            }
            var attachment = new ApprovalApplicationAttachment();
            attachment.setTenantId(actor.tenantId());
            attachment.setApplicationId(applicationId);
            attachment.setOwnerUserId(actor.userId());
            attachment.setFieldName(fieldName);
            attachment.setFieldType(fieldType);
            attachment.setDisplayName(name);
            attachment.setStorageKey(key);
            attachment.setMimeType(parsed.mimeType());
            attachment.setFileSize(file.getSize());
            attachment.setCreatedAt(LocalDateTime.now());
            if (attachments.insert(attachment) != 1) throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            audit.recordTransactional(actor.tenantId(), actor.userId(), "GENERIC_APPROVAL", applicationId.toString(),
                    "UPLOAD_ATTACHMENT", "SUCCESS", "attachmentId=" + attachment.getId());
            return response(attachment);
        } catch (IOException failure) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanupFailure) { log.warn("Unable to remove application upload temporary file"); }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ApprovalApplicationAttachmentResponse metadata(Long userId, Long applicationId, Long attachmentId, Long taskId) {
        return response(requireReadable(userId, applicationId, attachmentId, taskId));
    }

    @Override
    @Transactional(readOnly = true)
    public AttachmentContent content(Long userId, Long applicationId, Long attachmentId, Long taskId) {
        var attachment = requireReadable(userId, applicationId, attachmentId, taskId);
        return new AttachmentContent(storage.load(attachment.getStorageKey()), attachment.getMimeType(), attachment.getDisplayName());
    }

    @Override
    @Transactional(readOnly = true)
    public void validateReferences(Long tenantId, Long applicationId, String fieldName, String fieldType, List<?> references) {
        if (references.size() > 20 || applicationId == null && !references.isEmpty()) throw invalid();
        var seen = new HashSet<Long>();
        for (Object reference : references) {
            long id = referenceId(reference);
            if (!seen.add(id)) throw invalid();
            var attachment = attachments.selectById(id);
            if (attachment == null || !tenantId.equals(attachment.getTenantId()) || !applicationId.equals(attachment.getApplicationId())
                    || !fieldName.equals(attachment.getFieldName()) || !fieldType.equals(attachment.getFieldType())) throw invalid();
        }
    }

    private ApprovalApplicationAttachment requireReadable(Long userId, Long applicationId, Long attachmentId, Long taskId) {
        requirePositive(applicationId); requirePositive(attachmentId);
        var actor = actor(userId);
        var application = applications.selectById(applicationId);
        var attachment = attachments.selectById(attachmentId);
        if (application == null || attachment == null || !actor.tenantId().equals(application.getTenantId())
                || !actor.tenantId().equals(attachment.getTenantId()) || !applicationId.equals(attachment.getApplicationId()))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        Long instanceId = application.getWorkflowInstanceId();
        if (taskId != null) {
            requirePositive(taskId);
            var task = tasks.selectById(taskId);
            if (task == null || !actor.tenantId().equals(task.getTenantId()) || !"GENERIC_APPROVAL".equals(task.getBusinessType())
                    || !applicationId.equals(task.getBusinessId())) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            instanceId = task.getInstanceId();
        }
        boolean owner = actor.userId().equals(application.getApplicantUserId());
        boolean auditor = actor.permissions().contains("audit:read");
        if ("DRAFT".equals(application.getStatus()) && taskId == null) {
            if (!owner && !auditor) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
            return attachment;
        }
        if (!owner && !auditor) {
            if (instanceId == null || !actor.permissions().contains("todo:read")) throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
            boolean assigned = tasks.selectCount(new LambdaQueryWrapper<WorkflowTask>()
                    .eq(WorkflowTask::getTenantId, actor.tenantId()).eq(WorkflowTask::getInstanceId, instanceId)
                    .eq(WorkflowTask::getBusinessType, "GENERIC_APPROVAL").eq(WorkflowTask::getBusinessId, applicationId)
                    .eq(WorkflowTask::getAssigneeUserId, actor.userId())) > 0;
            if (!assigned && actions.countTargetAction(actor.tenantId(), instanceId, actor.userId(), "CC") == 0)
                throw new BusinessException(ErrorCode.RESOURCE_FORBIDDEN);
        }
        var snapshot = instanceId == null ? null : applications.selectExecution(actor.tenantId(), instanceId);
        if (snapshot == null || !actor.tenantId().equals(snapshot.tenantId())
                || !applicationId.equals(snapshot.applicationId()) || !application.getApplicantUserId().equals(snapshot.applicantUserId())
                || !references(snapshot.dataJson(), attachment.getFieldName(), attachmentId))
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        return attachment;
    }

    private boolean references(String dataJson, String fieldName, long id) {
        JsonNode values = read(dataJson).path(fieldName);
        if (!values.isArray() || values.size() > 20) return false;
        for (JsonNode value : values) if (value.isTextual() && value.asText().equals(Long.toString(id))) return true;
        return false;
    }

    private String fieldType(String schema, String fieldName) {
        if (fieldName == null || fieldName.isBlank() || fieldName.length() > 120) throw invalid();
        var fields = read(schema).path("fields");
        if (!fields.isArray() || fields.size() > 100) throw invalid();
        String type = null;
        for (JsonNode field : fields) if (fieldName.equals(field.path("name").asText())) {
            if (type != null) throw invalid();
            type = field.path("type").asText();
        }
        if (!Set.of("file", "image").contains(type == null ? "" : type)) throw invalid();
        return type;
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (Exception invalidJson) { throw invalid(); }
    }

    private long referenceId(Object value) {
        if (!(value instanceof String id) || !id.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(id); }
        catch (NumberFormatException invalidId) { throw invalid(); }
    }

    private ResolvedUserAccess actor(Long userId) {
        var actor = access.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        return actor;
    }

    private void requirePositive(Long id) {
        if (id == null || id < 1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
    }

    private String displayName(MultipartFile file) {
        String name = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").strip();
        if (name.isEmpty()) return "attachment";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private ApprovalApplicationAttachmentResponse response(ApprovalApplicationAttachment attachment) {
        return new ApprovalApplicationAttachmentResponse(attachment.getId().toString(), attachment.getFieldName(),
                attachment.getFieldType(), attachment.getDisplayName(), attachment.getMimeType(),
                attachment.getFileSize(), attachment.getCreatedAt());
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.REQUEST_INVALID, "validation.approval.attachment.invalid");
    }
}
