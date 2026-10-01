package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageNumber;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.pageSize;
import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.requiredText;

@Component
public final class DictionaryItemQueryToolHandler extends TypedReadToolHandler<DictionaryToolPort.DictionaryItemQuery, DictionaryToolPort.DictionaryItems> {
    private final DictionaryToolPort port;

    public DictionaryItemQueryToolHandler(DictionaryToolPort port, ObjectMapper mapper) {
        super(ToolCode.DICTIONARY_ITEM_QUERY, mapper);
        this.port = port;
    }

    @Override
    protected DictionaryToolPort.DictionaryItemQuery parseArguments(JsonNode arguments) {
        return new DictionaryToolPort.DictionaryItemQuery(requiredText(arguments, "typeCode"),
                optionalText(arguments, "keyword"), optionalText(arguments, "status"),
                pageNumber(arguments), pageSize(arguments));
    }

    @Override
    protected DictionaryToolPort.DictionaryItems invoke(
            TrustedToolContext context, DictionaryToolPort.DictionaryItemQuery query) {
        return port.dictionaryItems(context.actor(), query);
    }
}
