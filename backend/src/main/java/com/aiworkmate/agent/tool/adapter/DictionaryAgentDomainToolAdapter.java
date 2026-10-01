package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.DictionaryTypeRequest;
import com.aiworkmate.service.DataDictionaryService;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public class DictionaryAgentDomainToolAdapter implements DictionaryToolPort {
    private final DataDictionaryService dictionaryService;

    @Override
    public DictionaryOverview dictionaries(ToolActorContext context, DictionaryQuery query) {
        var response = dictionaryService.listTypes(context.userId(), query.keyword(), query.status());
        return new DictionaryOverview(response.records().stream()
                .map(type -> new DictionaryType(type.code(), type.name(), type.description(), type.status(),
                        type.sortOrder(), type.itemCount(), type.activeItemCount(), type.version(), type.updatedAt()))
                .toList(), response.canManage());
    }

    @Override
    public CreateTypeResult createType(ToolActorContext context, CreateTypeCommand command) {
        var response = dictionaryService.createTypeAgent(context.userId(),
                new DictionaryTypeRequest(command.code(), command.name(), command.description(),
                        command.sortOrder(), null));
        return new CreateTypeResult(response.id(), response.code(), response.name(), response.description(),
                response.status(), response.sortOrder(), response.version(), response.updatedAt());
    }
}
