package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalApplicationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.ApprovalApplicationResponse;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.GenericApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public final class ApprovalApplicationAgentDomainToolAdapter implements ApprovalApplicationToolPort {
    private final GenericApprovalService approvalService;

    @Override
    public WriteResult createDraft(ToolActorContext context, Draft command, String operationKey) {
        Map<String, Object> formData = new LinkedHashMap<>();
        for (FieldValue field : command.fields()) {
            Object value = field.multiple() ? field.values() : field.values().get(0);
            if (formData.putIfAbsent(field.name(), value) != null) {
                throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
        }
        ApprovalApplicationResponse created = approvalService.createAgentDraft(
                context.userId(),
                new ApprovalDraftRequest(command.formKey(), command.processKey(), formData),
                operationKey);
        return new WriteResult(created.id(), created.formKey(), created.status(), created.version());
    }

    @Override
    public WriteResult submitDraft(ToolActorContext context, long applicationId, int version) {
        ApprovalApplicationResponse submitted = approvalService.submitAgentDraft(
                context.userId(), applicationId, new VersionRequest(version));
        return new WriteResult(
                submitted.id(), submitted.formKey(), submitted.status(), submitted.version());
    }

    @Override
    public WriteResult withdraw(ToolActorContext context, long applicationId, int version) {
        ApprovalApplicationResponse withdrawn = approvalService.withdrawAgentApplication(
                context.userId(), applicationId, new VersionRequest(version));
        return new WriteResult(
                withdrawn.id(), withdrawn.formKey(), withdrawn.status(), withdrawn.version());
    }
}
