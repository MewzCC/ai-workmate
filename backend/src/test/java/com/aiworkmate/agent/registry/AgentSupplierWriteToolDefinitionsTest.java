package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSupplierWriteToolDefinitionsTest {
    @Test
    void createDraftExcludesSensitiveAndActivationFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentSupplierWriteToolDefinitions()
                .supplierCreateDraftToolDefinition(mapper);
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:9d859cfb46661e9c8994e04a29966aa378f2ef717eb5f62f6e8b5d434add1fdb");
        assertThat(definition.requiredPermissions()).containsExactly("supplier:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree("""
                {"code":"SUP-2027","name":"示例供应商","shortName":"示例",
                 "category":"SERVICE","supplierLevel":"STANDARD","paymentTerms":"月结30天"}
                """))).isTrue();
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree("""
                {"code":"SUP-2027","name":"示例供应商","category":"SERVICE",
                 "supplierLevel":"STANDARD","contactPhone":"13800000000"}
                """))).isFalse();
        assertThat(definition.inputSchema().path("properties").fieldNames()).toIterable()
                .doesNotContain("unifiedSocialCreditCode", "contactName", "contactPhone",
                        "contactEmail", "address", "riskNote", "status");
    }
}
