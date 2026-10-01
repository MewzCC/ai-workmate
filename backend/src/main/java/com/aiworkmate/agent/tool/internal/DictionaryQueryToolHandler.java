package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.DictionaryToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;

@Component
public final class DictionaryQueryToolHandler extends TypedReadToolHandler<DictionaryToolPort.DictionaryQuery, DictionaryToolPort.DictionaryOverview> {
    private final DictionaryToolPort port;
    public DictionaryQueryToolHandler(DictionaryToolPort port, ObjectMapper mapper) { super(ToolCode.DICTIONARY_QUERY, mapper); this.port = port; }
    @Override protected DictionaryToolPort.DictionaryQuery parseArguments(JsonNode a) { return new DictionaryToolPort.DictionaryQuery(optionalText(a, "keyword"), optionalText(a, "status")); }
    @Override protected DictionaryToolPort.DictionaryOverview invoke(TrustedToolContext c, DictionaryToolPort.DictionaryQuery q) { return port.dictionaries(c.actor(), q); }
}
