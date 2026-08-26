package com.aiworkmate.agent.registry;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class ToolCatalog {
    private final Map<String, ToolDefinition> definitions;

    public ToolCatalog(List<ToolDefinition> definitions) {
        Map<String, ToolDefinition> indexed = new LinkedHashMap<>();
        for (ToolDefinition definition : definitions) {
            definition.validate();
            if (indexed.putIfAbsent(definition.code(), definition) != null) {
                throw new IllegalStateException("Duplicate Agent tool code: " + definition.code());
            }
        }
        this.definitions = Map.copyOf(indexed);
    }

    public Optional<ToolDefinition> find(String code) {
        return Optional.ofNullable(definitions.get(code));
    }

    public Collection<ToolDefinition> all() {
        return definitions.values();
    }
}
