package com.aiworkmate.agent.registry;

import com.aiworkmate.agent.gateway.ToolSchemaValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AgentGenericApprovalTaskDetailToolDefinitionTest {
    private final ObjectMapper json = new ObjectMapper();
    @Test void freezesAnAssignedReadOnlyContractAndRejectsAuthorityOrUnboundedArguments() throws Exception {
        var tool = new AgentGenericApprovalTaskDetailToolDefinition().genericApprovalTaskDetailQueryToolDefinition(json);
        assertThat(tool.schemaHash()).isEqualTo("sha256:c1e6afc86677babeed0109482833332693c33021e08175aa986a1973e7061035");
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L0);
        assertThat(tool.sideEffect()).isEqualTo(SideEffect.NONE);
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(tool.requiredPermissions()).containsExactly("todo:read");
        var validator = new ToolSchemaValidator();
        assertThat(validator.valid(tool.inputSchema(), json.readTree("{\"taskId\":1,\"fieldPage\":25}"))).isTrue();
        for (String args : new String[]{"{}", "{\"taskId\":0}", "{\"taskId\":1,\"fieldPage\":26}",
                "{\"taskId\":1,\"fieldPage\":0}", "{\"taskId\":1,\"userId\":2}", "{\"taskId\":1,\"tenantId\":2}", "{\"taskId\":1,\"url\":\"https://example.invalid\"}"})
            assertThat(validator.valid(tool.inputSchema(), json.readTree(args))).as(args).isFalse();
    }
    @Test void fourWorstCaseFieldsStayUnderTheDeclaredOutputByteBudgetWithoutTruncation() throws Exception {
        var tool = new AgentGenericApprovalTaskDetailToolDefinition().genericApprovalTaskDetailQueryToolDefinition(json);
        var result = json.createObjectNode().put("taskId",1).put("applicationId",2).put("applicantName","A".repeat(120))
                .put("formName","F".repeat(200)).put("title","T".repeat(200)).put("nodeName","N".repeat(200))
                .put("status","PENDING").put("version",0).put("instanceStatus","RUNNING").put("applicationStatus","PENDING")
                .put("overdue",false).put("fieldsTotal",100).put("fieldPage",25).put("fieldPageSize",4)
                .put("canApprove",true).put("canReject",true).put("blockedReason","B".repeat(500));
        var fields = result.putArray("fields");
        for (int i=0;i<4;i++) {
            var field = fields.addObject().put("name","n".repeat(120)).put("label","L".repeat(200)).put("type","t".repeat(40)).put("redacted",false);
            var values = field.putArray("values");for(int j=0;j<20;j++)values.add(String.valueOf((char)0).repeat(500));
        }
        assertThat(new ToolSchemaValidator().valid(tool.outputSchema(), result)).isTrue();
        assertThat(json.writeValueAsBytes(result).length).isLessThan(tool.maxResultBytes());
    }
}
