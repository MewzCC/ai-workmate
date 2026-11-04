package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentGenericApprovalTaskApproveToolDefinitionTest {
    @Test void freezesOneVersionedAssignedWriteWithSecondaryConfirmationAndNoRetry() throws Exception {
        var json=new ObjectMapper();var tool=new AgentGenericApprovalTaskApproveToolDefinition().genericApprovalTaskApproveToolDefinition(json);
        assertThat(tool.schemaHash()).isEqualTo("sha256:c032df3ef5293e8fcf69271882635e3580eeec2097d911ce68d1cebb0808076d");
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L2);assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);assertThat(tool.requiredPermissions()).containsExactly("approval:act");
        var validator=new ToolSchemaValidator();assertThat(validator.valid(tool.inputSchema(),json.readTree("{\"taskId\":1,\"version\":0}"))).isTrue();
        for(String args:new String[]{"{}","{\"taskId\":0,\"version\":0}","{\"taskId\":1}","{\"taskId\":1,\"version\":-1}",
                "{\"taskId\":1,\"version\":2147483647}","{\"taskId\":1,\"version\":0,\"tenantId\":2}","{\"taskId\":1,\"version\":0,\"confirmed\":true}"})
            assertThat(validator.valid(tool.inputSchema(),json.readTree(args))).as(args).isFalse();
        assertThat(validator.valid(tool.inputSchema(),json.createObjectNode().put("taskId",1).put("version",0).put("comment","x".repeat(501)))).isFalse();
        var receipt=json.createObjectNode().put("applicationId",1).put("taskId",2).put("status","APPROVED").put("version",1).put("applicationStatus","PENDING").put("action","APPROVE");
        assertThat(validator.valid(tool.outputSchema(),receipt)).isTrue();receipt.put("applicationStatus","APPROVED");assertThat(validator.valid(tool.outputSchema(),receipt)).isTrue();
        receipt.put("status","PENDING");assertThat(validator.valid(tool.outputSchema(),receipt)).isFalse();
    }
}
