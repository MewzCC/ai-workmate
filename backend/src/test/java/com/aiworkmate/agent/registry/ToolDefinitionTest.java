package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolDefinitionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateStableSchemaHash() throws Exception {
        ToolDefinition first = definition(schema("page"), Set.of("todo:read"));
        ToolDefinition second = definition(schema("page"), Set.of("todo:read"));

        assertThat(first.schemaHash()).startsWith("sha256:").isEqualTo(second.schemaHash());
    }

    @Test
    void shouldRejectIdentityArgumentsAndRoutePermissions() throws Exception {
        assertThatThrownBy(() -> definition(schema("userId"), Set.of("todo:read")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forbidden tool argument");
        assertThatThrownBy(() -> definition(schema("page"), Set.of("route:todo-list")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("business permissions");

        JsonNode nestedIdentity = objectMapper.readTree("""
                {
                  "type":"object",
                  "properties":{"filter":{"type":"object","properties":{"tenantId":{"type":"integer"}},"additionalProperties":false}},
                  "additionalProperties":false
                }
                """);
        assertThatThrownBy(() -> definition(nestedIdentity, Set.of("todo:read")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Forbidden tool argument");
    }

    @Test
    void shouldRejectOpenSchemas() throws Exception {
        JsonNode openSchema = objectMapper.readTree("""
                {"type":"object","properties":{},"additionalProperties":true}
                """);
        assertThatThrownBy(() -> definition(openSchema, Set.of("todo:read")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("close additional properties");
    }

    @Test
    void shouldRejectSchemaReferencesAndExecutableContentHints() throws Exception {
        JsonNode referencedSchema = objectMapper.readTree("""
                {"type":"object","properties":{"page":{"$ref":"https://attacker.invalid/schema"}},"additionalProperties":false}
                """);
        assertThatThrownBy(() -> definition(referencedSchema, Set.of("todo:read")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema features");

        JsonNode executableSchema = objectMapper.readTree("""
                {"type":"object","properties":{"page":{"type":"string","contentMediaType":"text/html"}},"additionalProperties":false}
                """);
        assertThatThrownBy(() -> definition(executableSchema, Set.of("todo:read")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema features");
    }

    @Test
    void shouldRejectPermanentlyForbiddenCapabilityCodes() throws Exception {
        for (String code : Set.of(
                "sql.execute", "code.run", "file.read", "network.fetch", "access.modify",
                "record.delete", "batch.execute", "data.export", "message.send", "agent.autonomous")) {
            assertThatThrownBy(() -> definition(code, schema("page"), Set.of("todo:read")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Phase 2 capability boundary");
        }
    }

    private JsonNode schema(String property) throws Exception {
        return objectMapper.readTree("""
                {"type":"object","properties":{"%s":{"type":"integer"}},"additionalProperties":false}
                """.formatted(property));
    }

    private ToolDefinition definition(JsonNode schema, Set<String> permissions) throws Exception {
        return definition("todo.query", schema, permissions);
    }

    private ToolDefinition definition(String code, JsonNode schema, Set<String> permissions) throws Exception {
        JsonNode output = objectMapper.readTree("""
                {"type":"object","properties":{"items":{"type":"array"}},"additionalProperties":false}
                """);
        return ToolDefinition.create(
                code, "Todo query", "Query my todos", "Read-only self todos", "1.0.0",
                schema, output, RiskLevel.L0, permissions, PermissionMode.ALL, OwnershipPolicy.ASSIGNED_TO_SELF,
                RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE,
                50, 262144, 15000, "HASHED_ARGS"
        );
    }
}
