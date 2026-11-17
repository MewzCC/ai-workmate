package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AgentDocumentMetadataToolDefinitionsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ToolSchemaValidator validator = new ToolSchemaValidator();

    @Test void fixesEachDomainAsReadOnlyWithBoundedClosedArguments() throws Exception {
        var definitions = new AgentDocumentMetadataToolDefinitions();
        var employee = definitions.employeeDocumentMetadata(json);
        var seal = definitions.sealDocumentMetadata(json);
        assertThat(employee.schemaHash()).isEqualTo("sha256:4219cab2607cabe01052d7d7a52fdb1689c33b7e0e5e10c60b29996d3dc162d5");
        assertThat(seal.schemaHash()).isEqualTo("sha256:9ea5e25bb0c28519c4e8f04665dbc07a1ef5538e936fa914771940c812a3a1bd");
        assertThat(employee.requiredPermissions()).containsExactly("hr:read");
        assertThat(seal.requiredPermissions()).containsExactly("seal:register");
        for (var tool : List.of(employee, seal)) {
            assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L0);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.FIXED_RESOURCE);
            assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.READ_ONLY_SAFE);
            assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.NONE);
            String id = tool == employee ? "employeeId" : "usageId";
            var arguments = json.createObjectNode().put(id, 7).put("page", 2).put("size", 20);
            assertThat(validator.valid(tool.inputSchema(), arguments)).isTrue();
            for (String field : List.of("tenantId", "userId", "domain", "url", "documentId")) {
                var forged = arguments.deepCopy().put(field, "forbidden");
                assertThat(validator.valid(tool.inputSchema(), forged)).as(field).isFalse();
            }
            assertThat(validator.valid(tool.inputSchema(), arguments.deepCopy().put("size", 21))).isFalse();
            assertThat(validator.valid(tool.inputSchema(), arguments.deepCopy().put("page", 10001))).isFalse();
            assertThat(validator.valid(tool.inputSchema(), arguments.deepCopy().put(id, 0))).isFalse();
        }
    }

    @Test void rejectsStorageCoordinatesAndKeepsWorstCaseMetadataUnderBudget() throws Exception {
        var tool = new AgentDocumentMetadataToolDefinitions().employeeDocumentMetadata(json);
        var output = json.createObjectNode().put("total", 20).put("page", 1).put("size", 20);
        var items = output.putArray("items");
        for (int index = 0; index < 20; index++) {
            items.addObject().put("documentId", "9007199254740993").put("documentType", "CONTRACT")
                    .put("displayName", "\u0000".repeat(255)).put("mimeType", "\u0000".repeat(120))
                    .put("fileSize", Long.MAX_VALUE).put("createdAt", "\u0000".repeat(32));
        }
        assertThat(validator.valid(tool.outputSchema(), output)).isTrue();
        assertThat(json.writeValueAsBytes(output).length).isLessThan(tool.maxResultBytes());
        ((com.fasterxml.jackson.databind.node.ObjectNode) items.get(0)).put("storageKey", "/private/object");
        assertThat(validator.valid(tool.outputSchema(), output)).isFalse();
    }
}
