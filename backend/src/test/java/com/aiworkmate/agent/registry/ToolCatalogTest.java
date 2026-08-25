package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolCatalogTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldRejectDuplicateToolCodes() throws Exception {
        ToolDefinition definition = definition();
        assertThatThrownBy(() -> new ToolCatalog(List.of(definition, definition)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate Agent tool code");
    }

    private ToolDefinition definition() throws Exception {
        JsonNode schema = objectMapper.readTree("""
                {"type":"object","properties":{},"additionalProperties":false}
                """);
        return ToolDefinition.create(
                "todo.query", "Todo query", "Query my todos", "Read-only self todos", "1.0.0",
                schema, schema, RiskLevel.L0, Set.of("todo:read"), PermissionMode.ALL,
                OwnershipPolicy.ASSIGNED_TO_SELF, RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE,
                ConfirmationPolicy.NONE, 50, 262144, 15000, "HASHED_ARGS"
        );
    }
}
