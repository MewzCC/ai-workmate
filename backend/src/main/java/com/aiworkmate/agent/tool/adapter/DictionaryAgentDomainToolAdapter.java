package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.DictionaryTypeRequest;
import com.aiworkmate.dto.DictionaryTypeAgentUpdateRequest;
import com.aiworkmate.dto.DictionaryItemAgentCreateRequest;
import com.aiworkmate.dto.DictionaryItemAgentUpdateRequest;
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
    public DictionaryItems dictionaryItems(ToolActorContext context, DictionaryItemQuery query) {
        var response = dictionaryService.listItemsAgent(context.userId(), query.typeCode(), query.keyword(),
                query.status(), query.page(), query.size());
        return new DictionaryItems(query.typeCode(), response.records().stream()
                .map(item -> new DictionaryItem(item.value(), item.label(), item.description(), item.status(),
                        item.sortOrder(), item.usageCount(), item.version(), item.updatedAt()))
                .toList(), response.total(), response.page(), response.size(), response.canManage());
    }

    @Override
    public CreateTypeResult createType(ToolActorContext context, CreateTypeCommand command) {
        var response = dictionaryService.createTypeAgent(context.userId(),
                new DictionaryTypeRequest(command.code(), command.name(), command.description(),
                        command.sortOrder(), null));
        return new CreateTypeResult(response.id(), response.code(), response.name(), response.description(),
                response.status(), response.sortOrder(), response.version(), response.updatedAt());
    }

    @Override
    public UpdateTypeResult updateType(ToolActorContext context, UpdateTypeCommand command) {
        var response = dictionaryService.updateTypeAgent(context.userId(), command.code(),
                new DictionaryTypeAgentUpdateRequest(command.version(), command.name(),
                        command.description(), command.sortOrder()));
        return new UpdateTypeResult(response.id(), response.code(), response.name(), response.description(),
                response.status(), response.sortOrder(), response.version(), response.updatedAt());
    }

    @Override
    public CreateItemResult createItem(ToolActorContext context, CreateItemCommand command) {
        var response = dictionaryService.createItemAgent(context.userId(), command.typeCode(),
                new DictionaryItemAgentCreateRequest(command.value(), command.label(),
                        command.description(), command.sortOrder()));
        return new CreateItemResult(response.id(), command.typeCode(), response.value(), response.label(),
                response.description(), response.status(), response.sortOrder(), response.usageCount(),
                response.version(), response.updatedAt());
    }

    @Override
    public UpdateItemResult updateItem(ToolActorContext context, UpdateItemCommand command) {
        var response = dictionaryService.updateItemAgent(context.userId(), command.typeCode(), command.value(),
                new DictionaryItemAgentUpdateRequest(command.version(), command.label(),
                        command.description(), command.sortOrder()));
        return new UpdateItemResult(response.id(), command.typeCode(), response.value(), response.label(),
                response.description(), response.status(), response.sortOrder(), response.usageCount(),
                response.version(), response.updatedAt());
    }
}
