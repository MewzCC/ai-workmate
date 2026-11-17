package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentSealDecisionToolDefinitionsTest {
    @Test void freezesTwoAssignedL2WritesAndSeparatesTaskAndUsageVersions() throws Exception {
        var json=new ObjectMapper();var definitions=new AgentSealDecisionToolDefinitions();
        var approve=definitions.sealApproveToolDefinition(json);
        var reject=definitions.sealRejectToolDefinition(json);
        assertThat(approve.schemaHash()).isEqualTo("sha256:002f1702879a327dd4e1b93492dc7ed1207fd94357947227f9b5d0095b509e78");
        assertThat(reject.schemaHash()).isEqualTo("sha256:799f7a4f8da087bb59453dbc293d1ec3ddd60e97644395a57251735c361861bb");
        var validator=new ToolSchemaValidator();
        for(var tool:java.util.List.of(approve,reject)) {
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L2);
            assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
            assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
            assertThat(tool.requiredPermissions()).containsExactly("approval:act");
            assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"taskId\":3,\"version\":2,\"comment\":\"Reason\"}"))).isTrue();
            assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"usageId\":3,\"version\":2,\"comment\":\"Reason\"}"))).isFalse();
            assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"taskId\":3,\"version\":2,\"comment\":\"Reason\",\"tenantId\":1}"))).isFalse();
        }
        assertThat(validator.valid(reject.inputSchema(),json.readTree("{\"taskId\":3,\"version\":2}"))).isFalse();
        var receipt=json.readTree("{\"resourceId\":9,\"resourceType\":\"SEAL_USAGE\",\"resourceStatus\":\"APPROVED\",\"resourceVersion\":5,\"taskId\":3,\"status\":\"APPROVED\",\"version\":3,\"action\":\"APPROVE\"}");
        assertThat(validator.valid(approve.outputSchema(),receipt)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode)receipt).put("resourceType","VISITOR_BOOKING");
        assertThat(validator.valid(approve.outputSchema(),receipt)).isFalse();
    }
}
