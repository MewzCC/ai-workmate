package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.ApprovalFormAgentDraftRequest;
import com.aiworkmate.dto.ApprovalFormAgentDraftUpdateRequest;
import com.aiworkmate.service.ApprovalEngineService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class ApprovalConfigurationAgentDomainToolAdapter implements ApprovalConfigurationToolPort {
    private final ApprovalEngineService approvalEngineService;

    @Override
    public Page query(ToolActorContext context, Query query) {
        return switch (query.resource()) {
            case FORM -> {
                var result = approvalEngineService.listForms(
                        context.userId(), query.keyword(), query.status(), query.page(), query.size());
                yield new Page(result.records().stream().map(item ->
                        new Item(item.id(), Resource.FORM, item.formKey(), item.formName(), item.description(),
                                item.status(), item.version(), null, null, null, item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
            case PROCESS -> {
                var result = approvalEngineService.listProcesses(
                        context.userId(), query.keyword(), query.status(), query.page(), query.size());
                yield new Page(result.records().stream().map(item ->
                        new Item(item.id(), Resource.PROCESS, item.processKey(), item.processName(), item.description(),
                                item.status(), item.version(), item.formName(), null, null, item.updatedAt())).toList(),
                        result.total(), result.page(), result.size());
            }
            case RULE -> {
                var result = approvalEngineService.listRules(
                        context.userId(), query.keyword(), query.status(), query.page(), query.size());
                yield new Page(result.records().stream().map(item ->
                        new Item(item.id(), Resource.RULE, item.ruleKey(), item.ruleName(), item.description(),
                                item.status(), item.version(), null, item.ruleType(), item.priority(),
                                item.updatedAt())).toList(), result.total(), result.page(), result.size());
            }
        };
    }

    @Override
    public FormDraftResult createFormDraft(ToolActorContext context, FormDraft command) {
        var request = new ApprovalFormAgentDraftRequest(command.formKey(), command.formName(),
                command.description(), command.fields().stream().map(field ->
                new ApprovalFormAgentDraftRequest.Field(field.name(), field.label(), field.type(),
                        field.required(), field.placeholder(), field.options(), field.width())).toList());
        var response = approvalEngineService.createFormDraftAgent(context.userId(), request);
        return new FormDraftResult(response.id(), response.formKey(), response.status(),
                response.version(), response.updatedAt());
    }

    @Override
    public FormDraftResult updateFormDraft(ToolActorContext context, FormDraftUpdate command) {
        var request = new ApprovalFormAgentDraftUpdateRequest(command.version(), command.formName(),
                command.description(), command.fields().stream().map(field ->
                new ApprovalFormAgentDraftUpdateRequest.Field(field.name(), field.label(), field.type(),
                        field.required(), field.placeholder(), field.options(), field.width())).toList());
        var response = approvalEngineService.updateFormDraftAgent(context.userId(), command.formId(), request);
        return new FormDraftResult(response.id(), response.formKey(), response.status(),
                response.version(), response.updatedAt());
    }
}
