package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentVisitorDecisionToolDefinitionsTest {
    @Test void freezesTwoAssignedL2WritesAndSeparatesTaskAndBookingVersions() throws Exception {
        var json=new ObjectMapper();var definitions=new AgentVisitorDecisionToolDefinitions();
        var approve=definitions.visitorApproveToolDefinition(json);
        var reject=definitions.visitorRejectToolDefinition(json);
        assertThat(approve.schemaHash()).isEqualTo("sha256:8b22e26d324740577f6bf91ba4b639b150e88dbb505b3f3f71c895729e1bdb19");
        assertThat(reject.schemaHash()).isEqualTo("sha256:f52f018c3ee70fb304651a915c223fc1da0f8de16ff68cc87502e00e3bc16deb");
        var validator=new ToolSchemaValidator();
        for(var tool:java.util.List.of(approve,reject)) {
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L2);
            assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
            assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
            assertThat(tool.requiredPermissions()).containsExactly("approval:act");
            assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"taskId\":3,\"version\":2,\"comment\":\"Reason\"}"))).isTrue();
            assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"bookingId\":3,\"version\":2,\"comment\":\"Reason\"}"))).isFalse();
            assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"taskId\":3,\"version\":2,\"comment\":\"Reason\",\"tenantId\":1}"))).isFalse();
        }
        assertThat(validator.valid(reject.inputSchema(),json.readTree("{\"taskId\":3,\"version\":2}"))).isFalse();
        var receipt=json.readTree("{\"resourceId\":9,\"resourceType\":\"VISITOR_BOOKING\",\"resourceStatus\":\"APPROVED\",\"resourceVersion\":5,\"taskId\":3,\"status\":\"APPROVED\",\"version\":3,\"action\":\"APPROVE\"}");
        assertThat(validator.valid(approve.outputSchema(),receipt)).isTrue();
        ((com.fasterxml.jackson.databind.node.ObjectNode)receipt).put("resourceType","SEAL_USAGE");
        assertThat(validator.valid(approve.outputSchema(),receipt)).isFalse();
    }
}
