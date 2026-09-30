package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.GenericApprovalService;
import lombok.RequiredArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class ApprovalApplicationAgentDomainToolAdapter implements ApprovalApplicationToolPort {
    private final GenericApprovalService approvalService;

    @Override
    public WriteResult createDraft(ToolActorContext context, Draft command, ToolOperationKey operationKey) {
        ApprovalApplicationResponse created = approvalService.createAgentDraft(
                context.userId(),
                new ApprovalDraftRequest(command.formKey(), command.processKey(), formData(command.fields())),
                operationKey.value());
        return new WriteResult(created.id(), created.formKey(), created.status(), created.version());
    }

    @Override
    public WriteResult updateDraft(
            ToolActorContext context, long applicationId, int version, DraftUpdate command) {
        ApprovalApplicationResponse updated = approvalService.updateAgentDraft(
                context.userId(), applicationId,
                new com.aiworkmate.dto.ApprovalDraftUpdateRequest(
                        command.processKey(), formData(command.fields()), version));
        return new WriteResult(updated.id(), updated.formKey(), updated.status(), updated.version());
    }

    @Override
    public WriteResult submitDraft(ToolActorContext context, long applicationId, int version) {
        ApprovalApplicationResponse submitted = approvalService.submitAgentDraft(
                context.userId(), applicationId, new VersionRequest(version));
        return new WriteResult(
                submitted.id(), submitted.formKey(), submitted.status(), submitted.version());
    }

    @Override
    public WriteResult cancelDraft(ToolActorContext context, long applicationId, int version) {
        ApprovalApplicationResponse cancelled = approvalService.cancelAgentDraft(
                context.userId(), applicationId, new VersionRequest(version));
        return new WriteResult(
                cancelled.id(), cancelled.formKey(), cancelled.status(), cancelled.version());
    }

    @Override
    public WriteResult withdraw(ToolActorContext context, long applicationId, int version) {
        ApprovalApplicationResponse withdrawn = approvalService.withdrawAgentApplication(
                context.userId(), applicationId, new VersionRequest(version));
        return new WriteResult(
                withdrawn.id(), withdrawn.formKey(), withdrawn.status(), withdrawn.version());
    }

    @Override
    public WriteResult reopen(ToolActorContext context, long applicationId, int version) {
        ApprovalApplicationResponse reopened = approvalService.reopenAgentApplication(
                context.userId(), applicationId, new VersionRequest(version));
        return new WriteResult(
                reopened.id(), reopened.formKey(), reopened.status(), reopened.version());
    }

    @Override
    public WriteResult remind(ToolActorContext context, long applicationId, int version) {
        ApprovalApplicationResponse reminded = approvalService.remind(
                context.userId(), applicationId, new VersionRequest(version));
        return new WriteResult(
                reminded.id(), reminded.formKey(), reminded.status(), reminded.version());
    }

    private Map<String, Object> formData(java.util.List<FieldValue> fields) {
        Map<String, Object> formData = new LinkedHashMap<>();
        for (FieldValue field : fields) {
            Object value = field.multiple() ? field.values() : field.values().get(0);
            if (formData.putIfAbsent(field.name(), value) != null) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
        }
        return formData;
    }
}
