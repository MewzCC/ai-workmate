package com.aiworkmate.agent.registry;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GenericCollaborationBoundaryTest {
    @Test void writeProfilesAreSecondaryVersionBoundAndRejectIdentityOrWrongActionFields()throws Exception{
        var json=new com.fasterxml.jackson.databind.ObjectMapper();var definitions=new AgentGenericCollaborationToolDefinitions();
        var validator=new com.aiworkmate.agent.gateway.ToolSchemaValidator();
        for(var tool:java.util.List.of(definitions.genericTransfer(json),definitions.genericCopy(json))){
            assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L2);assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
            assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);assertThat(tool.sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
            assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);assertThat(tool.requiredPermissions()).containsExactlyInAnyOrder("approval:act","todo:read");
            var args=json.createObjectNode().put("taskId",1).put("targetUserId",2).put("version",0).put("reason","Explicit reason");
            assertThat(validator.valid(tool.inputSchema(),args)).isTrue();args.put("tenantId",9);assertThat(validator.valid(tool.inputSchema(),args)).isFalse();args.remove("tenantId");
            args.remove("reason");assertThat(validator.valid(tool.inputSchema(),args)).isFalse();
            var receipt=json.createObjectNode().put("applicationId",1).put("taskId",2).put("status","PENDING").put("version",1).put("applicationStatus","PENDING")
                    .put("action",tool.code().endsWith("transfer")?"TRANSFER":"CC").put("originalAssigneeUserId",3).put("targetUserId",4).put("assigneeUserId",4);
            assertThat(validator.valid(tool.outputSchema(),receipt)).isTrue();receipt.put("action","APPROVE");assertThat(validator.valid(tool.outputSchema(),receipt)).isFalse();
        }
    }
    @Test void allThreeCapabilitiesAreCodeOwnedAndWritesAreExplicit(){
        var pages=new com.aiworkmate.agent.capability.PageCapabilityCatalog();
        for(String code:new String[]{"approval.genericTask.participants.query","approval.genericTask.transfer","approval.genericTask.cc"}){
            assertThat(ToolCode.isSupported(code)).as(code).isTrue();
            assertThat(ToolCode.fromCode(code).isWrite()).isEqualTo(!code.endsWith(".query"));
            assertThat(pages.allowsTool("ai-workspace",code,java.util.Set.of("route:ai-workspace"))).isFalse();
            assertThat(pages.allowsTool("ai-workspace",code,java.util.Set.of("route:ai-workspace","route:todo"))).isTrue();
            assertThat(pages.allowsTool("meeting-room",code,java.util.Set.of("route:meeting-room","route:todo"))).isFalse();
        }
    }
}
