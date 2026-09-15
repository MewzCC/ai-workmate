package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ApprovalConfigurationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.ApprovalEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
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
}
