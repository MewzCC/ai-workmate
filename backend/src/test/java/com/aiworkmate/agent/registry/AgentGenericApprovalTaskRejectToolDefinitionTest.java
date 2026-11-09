package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentGenericApprovalTaskRejectToolDefinitionTest {
    @Test void requiresAReasonAndFreezesOneAssignedL2DecisionWithoutRetry() throws Exception {
        var json=new ObjectMapper();
        var tool=new AgentGenericApprovalTaskRejectToolDefinition().genericApprovalTaskRejectToolDefinition(json);
        assertThat(tool.schemaHash()).isEqualTo("sha256:902b48b79d40b1538db4e8937e4e1d14bb6f428caa2e7ec60b3d84c61272b96d");
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(tool.requiredPermissions()).containsExactly("approval:act");
        var validator=new ToolSchemaValidator();
        assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"taskId\":1,\"version\":0,\"comment\":\"Missing evidence\"}"))).isTrue();
        for(String args:new String[]{"{}","{\"taskId\":1,\"version\":0}","{\"taskId\":1,\"version\":0,\"comment\":\"\"}",
                "{\"taskId\":0,\"version\":0,\"comment\":\"Reason\"}","{\"taskId\":1,\"version\":2147483647,\"comment\":\"Reason\"}",
                "{\"taskId\":1,\"version\":0,\"comment\":\"Reason\",\"tenantId\":2}"})
            assertThat(validator.valid(tool.inputSchema(),json.readTree(args))).as(args).isFalse();
        assertThat(validator.valid(tool.inputSchema(),json.createObjectNode().put("taskId",1).put("version",0).put("comment","x".repeat(501)))).isFalse();
        var receipt=json.createObjectNode().put("applicationId",1).put("taskId",2).put("status","REJECTED").put("version",1).put("applicationStatus","REJECTED").put("action","REJECT");
        assertThat(validator.valid(tool.outputSchema(),receipt)).isTrue();
        receipt.put("applicationStatus","PENDING");assertThat(validator.valid(tool.outputSchema(),receipt)).isFalse();
    }
}
