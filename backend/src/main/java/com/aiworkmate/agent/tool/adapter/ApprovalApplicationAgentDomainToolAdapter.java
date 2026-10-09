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
    public com.aiworkmate.agent.tool.port.ToolPage<Summary> mine(ToolActorContext context, Query query) {
        var page = approvalService.mineForAgent(context.userId(), query.status(), query.formKey(), query.page(), query.size());
        return new com.aiworkmate.agent.tool.port.ToolPage<>(page.records().stream().map(this::summary).toList(),
                page.total(), page.page(), page.size());
    }

    @Override
    public Detail detailMine(ToolActorContext context, DetailQuery query) {
        var value = approvalService.detailMineForAgent(context.userId(), query.applicationId(), query.fieldPage());
        var application = value.application();
        var summary = new Summary(application.applicationId(), application.formKey(), application.formName(),
                application.status(), application.version(), timestamp(application.submittedAt()),
                timestamp(application.completedAt()), timestamp(application.createdAt()), application.overdue(),
                application.reminderCount(), application.canWithdraw(), application.canEditDraft(),
                application.canCancel(), application.canRemind());
        return new Detail(summary, value.schemaSource(), value.formVersion(),
                value.fields().stream().map(field -> new DetailField(field.name(), field.label(), field.type(),
                        field.values(), field.redacted())).toList(), value.fieldsTotal(), value.fieldPage());
    }

    private Summary summary(ApprovalApplicationResponse value) {
        return new Summary(
                value.id(), value.formKey(), value.formName(), value.status(), value.version(),
                timestamp(value.submittedAt()), timestamp(value.completedAt()), timestamp(value.createdAt()),
                value.overdue(), value.reminderCount(), value.canWithdraw(), value.canEditDraft(),
                value.canCancel(), value.canRemind());
    }

    private String timestamp(java.time.LocalDateTime value) {
        return value == null ? null : value.toString();
    }

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
