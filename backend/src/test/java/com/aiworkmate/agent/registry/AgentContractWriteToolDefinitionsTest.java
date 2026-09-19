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
}
