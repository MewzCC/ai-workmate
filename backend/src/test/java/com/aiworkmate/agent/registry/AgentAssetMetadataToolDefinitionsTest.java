package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AgentAssetMetadataToolDefinitionsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentAssetMetadataToolDefinitions definitions = new AgentAssetMetadataToolDefinitions();
    private final ToolSchemaValidator validator = new ToolSchemaValidator();
    @Test void freezesConfirmedNonRetryableSingleResourceContracts() throws Exception {
        var create = definitions.assetCreateToolDefinition(mapper);
        var update = definitions.assetUpdateToolDefinition(mapper);
        assertThat(create.schemaHash()).isEqualTo("sha256:de7519ee9fd9f0c13b082e4754f2cd96435b0fdada088d70e2fa4f5933f14e5e");
        assertThat(update.schemaHash()).isEqualTo("sha256:84879740ee0225517991384a90bf2794fcdcf6675de9b8b551d4c3018c6717cc");
        for (var tool : java.util.List.of(create, update)) {
            assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
            assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L1);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
            assertThat(tool.requiredPermissions()).containsExactly("asset:write");
        }
    }
    @Test void rejectsHiddenAuthorityLifecycleChangesAndInvalidMoney() throws Exception {
        var schema = definitions.assetUpdateToolDefinition(mapper).inputSchema();
        for (String extra : new String[]{"\"tenantId\":2", "\"userId\":1", "\"status\":\"IDLE\"", "\"departmentId\":3",
                "\"ownerUserId\":4", "\"originalValue\":-1", "\"originalValue\":1.001", "\"originalValue\":1000000000000",
                "\"clearPurchaseDate\":false", "\"purchaseDate\":\"2026-01-02\",\"clearPurchaseDate\":true"}) {
            assertThat(validator.valid(schema, mapper.readTree("{\"assetId\":1,\"version\":0," + extra + "}"))).isFalse();
        }
        assertThat(validator.valid(schema, mapper.readTree("{\"assetId\":1,\"version\":0}"))).isFalse();
        assertThat(validator.valid(schema, mapper.readTree("{\"assetId\":1,\"version\":0,\"remark\":\"\",\"originalValue\":0}"))).isTrue();
        assertThat(validator.valid(schema, mapper.readTree("{\"assetId\":1,\"version\":0,\"clearPurchaseDate\":true}"))).isTrue();
        assertThat(validator.valid(definitions.assetCreateToolDefinition(mapper).inputSchema(),
                mapper.readTree("{\"assetCode\":\"A1\",\"name\":\"Laptop\",\"category\":\"IT\",\"departmentId\":2}"))).isTrue();
        assertThat(validator.valid(definitions.assetCreateToolDefinition(mapper).inputSchema(), mapper.readTree("{\"name\":\"X\"}"))).isFalse();
    }
}
