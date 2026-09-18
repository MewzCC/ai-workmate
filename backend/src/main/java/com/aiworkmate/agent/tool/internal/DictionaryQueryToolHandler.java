package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.OperationalGovernanceToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.optionalText;

@Component
public final class DictionaryQueryToolHandler extends TypedReadToolHandler<OperationalGovernanceToolPort.DictionaryQuery, OperationalGovernanceToolPort.DictionaryOverview> {
    private final OperationalGovernanceToolPort port;
    public DictionaryQueryToolHandler(OperationalGovernanceToolPort port, ObjectMapper mapper) { super(ToolCode.DICTIONARY_QUERY, mapper); this.port = port; }
    @Override protected OperationalGovernanceToolPort.DictionaryQuery parseArguments(JsonNode a) { return new OperationalGovernanceToolPort.DictionaryQuery(optionalText(a, "keyword"), optionalText(a, "status")); }
    @Override protected OperationalGovernanceToolPort.DictionaryOverview invoke(TrustedToolContext c, OperationalGovernanceToolPort.DictionaryQuery q) { return port.dictionaries(c.actor(), q); }
}
