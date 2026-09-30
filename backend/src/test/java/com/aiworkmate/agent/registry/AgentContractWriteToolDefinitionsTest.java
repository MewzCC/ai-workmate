package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentContractWriteToolDefinitionsTest {
    @Test
    void createDraftIsBoundedAndCannotActivatePaySignOrSend() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentContractWriteToolDefinitions()
                .contractCreateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:bd88de26d791f44a7d830c3786729fd65efa377500a30a3e0539758d646cba68");
        assertThat(definition.requiredPermissions()).containsExactly("contract:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.TENANT_SCOPED);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"code":"HT-2027","name":"年度采购合同","contractType":"PURCHASE",
                 "counterpartyName":"示例公司","ownerUserId":7,"amount":100000.00,"currency":"CNY",
                 "signedDate":"2026-09-01","startDate":"2026-09-01","endDate":"2027-08-31",
                 "summary":"年度采购"}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"code":"HT-2027","name":"年度采购合同","contractType":"PURCHASE",
                 "counterpartyName":"示例公司","ownerUserId":7,"amount":100000.00,"currency":"CNY",
                 "startDate":"2026-09-01","endDate":"2027-08-31","status":"ACTIVE"}
                """))).isFalse();
        assertThat(definition.inputSchema().path("properties").has("paymentAmount")).isFalse();
        assertThat(definition.inputSchema().path("properties").has("sendTo")).isFalse();
    }

    @Test
    void updateDraftUsesVersionAndCannotChangeCodeOrStatus() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentContractWriteToolDefinitions()
                .contractUpdateDraftToolDefinition(mapper);
        ToolSchemaValidator validator = new ToolSchemaValidator();
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:c5b10829bb43ebcfd88fd91a1cd9b167d6c62f9fc1e359fb5788b03f33b10b95");
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"contractId":81,"version":0,"name":"年度采购合同","contractType":"PURCHASE",
                 "counterpartyName":"示例公司","ownerUserId":7,"amount":100000.00,"currency":"CNY",
                 "startDate":"2026-09-01","endDate":"2027-08-31"}
                """))).isTrue();
        assertThat(definition.inputSchema().path("properties").fieldNames()).toIterable()
                .doesNotContain("code", "status", "paidAmount", "fulfillmentStatus", "sendTo");
    }

    @Test
    void statusUpdateRequiresSecondaryConfirmationAndClosedArguments() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentContractWriteToolDefinitions()
                .contractUpdateStatusToolDefinition(mapper);
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:10210557fa790ede65d98aadab50a9ea4a82648c21299e731e69e1f61008ab1a");
        assertThat(definition.requiredPermissions()).containsExactly("contract:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree("""
                {"contractId":81,"version":2,"status":"TERMINATED","reason":"双方协商终止"}
                """))).isTrue();
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree("""
                {"contractId":81,"version":2,"status":"PAID"}
                """))).isFalse();
    }

    @Test
    void fulfillmentUpdateRequiresSecondaryConfirmationAndClosedArguments() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ToolDefinition definition = new AgentContractWriteToolDefinitions()
                .contractUpdateFulfillmentToolDefinition(mapper);
        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:d6db3babd6a6432c6f4621cf711fffeda272c3b65f18d34266700235dd620556");
        assertThat(definition.requiredPermissions()).containsExactly("contract:manage");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree("""
                {"contractId":81,"version":3,"status":"BREACHED","reason":"交付逾期"}
                """))).isTrue();
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree("""
                {"contractId":81,"version":3,"status":"NOT_STARTED"}
                """))).isFalse();
    }
}
