package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentApprovalApplicationWriteToolDefinitionsTest {
    @Test
    void tableInputIsBoundedStructuredAndExclusiveOfScalarValues() throws Exception {
        var mapper = new ObjectMapper();
        var schema = new AgentApprovalApplicationWriteToolDefinitions().approvalApplicationCreateDraftToolDefinition(mapper).inputSchema();
        var validator = new ToolSchemaValidator();
        var arguments = mapper.readTree("""
                {"formKey":"expense","fields":[{"name":"items","rows":[{"cells":[
                  {"name":"amount","value":"9007199254740993.01"}]}]}]}
                """);
        assertThat(validator.valid(schema, arguments)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode) arguments.path("fields").get(0)).put("value", "ambiguous");
        assertThat(validator.valid(schema, arguments)).isFalse();
        assertThat(schema.path("properties").path("fields").path("items").path("properties").path("rows").path("maxItems").asInt()).isEqualTo(20);
    }
    @Test
    void genericDraftUsesClosedTypedFieldsAndBusinessIdempotency() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationCreateDraftToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:d81ae202169689c1af702ca5ef36f53a1f0908f6236c0ca44ac2be5232faaff7");
        assertThat(definition.requiredPermissions()).containsExactly("approval:create");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.BUSINESS_IDEMPOTENT);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formKey":"expense","fields":[{"name":"reason","value":"客户拜访"}]}
                """))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree("""
                {"formKey":"expense","fields":[],"tenantId":9}
                """))).isFalse();
    }

    @Test
    void genericDraftSubmitUsesOwnedVersionedNonRetryableWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationSubmitDraftToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:edbe7ad52b50a98e339f5e7cf83b572c26d3b71ddccb1f50aa11496fbed80426");
        assertThat(definition.requiredPermissions()).containsExactly("approval:submit");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":2,\"tenantId\":9}"))).isFalse();
    }

    @Test
    void genericApplicationWithdrawUsesOwnedVersionedNonRetryableWrite() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationWithdrawToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:35ce035131933d4068d30efa845969773cacbfbe9d329ef8bc4aa17f291ac6fd");
        assertThat(definition.requiredPermissions()).containsExactly("approval:withdraw");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":2}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":2,\"reason\":\"绕过\"}"))).isFalse();
    }

    @Test
    void genericApplicationReopenRestoresOnlyOneOwnedDraft() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationReopenToolDefinition(mapper);
        var validator = new ToolSchemaValidator();

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:3c896994338b1fea1ecd2afcdfb96f200a4ff0ad3f9f74856cd0e3acda290241");
        assertThat(definition.requiredPermissions()).containsExactly("approval:reopen");
        assertThat(definition.riskLevel()).isEqualTo(RiskLevel.L1);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":4}"))).isTrue();
        assertThat(validator.valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":4,\"submit\":true}"))).isFalse();
    }

    @Test
    void genericApplicationReminderIsOwnedRateLimitedAndNonRetryable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationRemindToolDefinition(mapper);

        assertThat(definition.requiredPermissions()).containsExactly("approval:remind");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(definition.confirmationPolicy()).isEqualTo(ConfirmationPolicy.EXPLICIT);
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":4}"))).isTrue();
    }

    @Test
    void genericDraftUpdateUsesClosedFieldsAndExpectedVersion() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationUpdateDraftToolDefinition(mapper);

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:5230372a18d3c297184e08e1c6877f66c327afdf923b5b623390f31a78e58504");
        assertThat(definition.requiredPermissions()).containsExactly("approval:create");
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree("""
                {"applicationId":10,"version":2,"fields":[{"name":"reason","value":"客户拜访"}]}
                """))).isTrue();
    }

    @Test
    void genericDraftCancelUsesDedicatedPermissionAndPreservesAuditRecord() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var definition = new AgentApprovalApplicationWriteToolDefinitions()
                .approvalApplicationCancelDraftToolDefinition(mapper);

        assertThat(definition.schemaHash()).isEqualTo(
                "sha256:4f107bb62068d65d559bf3c36366f59f30d6ef60d209a4576f3f9480620ca808");
        assertThat(definition.requiredPermissions()).containsExactly("approval:cancel");
        assertThat(definition.ownershipPolicy()).isEqualTo(OwnershipPolicy.SELF);
        assertThat(definition.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(new ToolSchemaValidator().valid(definition.inputSchema(), mapper.readTree(
                "{\"applicationId\":10,\"version\":2}"))).isTrue();
    }
}
