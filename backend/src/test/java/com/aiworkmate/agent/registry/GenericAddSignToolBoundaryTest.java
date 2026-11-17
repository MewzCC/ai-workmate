package com.aiworkmate.agent.registry;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GenericAddSignToolBoundaryTest {
    @Test void requiresSecondaryConfirmationAndBoundedAuthorityFreeArguments() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var tool = new AgentGenericAddSignToolDefinition().genericAddSign(json);
        var validator = new com.aiworkmate.agent.gateway.ToolSchemaValidator();
        assertThat(tool.schemaHash()).isEqualTo("sha256:b6e660a59cd63babffaf3d93758a5b516fdba2083d9b1cba75a2282240e7c0d0");
        assertThat(tool.riskLevel()).isEqualTo(RiskLevel.L2);
        assertThat(tool.confirmationPolicy()).isEqualTo(ConfirmationPolicy.SECONDARY);
        assertThat(tool.retryPolicy()).isEqualTo(RetryPolicy.NEVER);
        assertThat(tool.ownershipPolicy()).isEqualTo(OwnershipPolicy.ASSIGNED_TO_SELF);
        assertThat(tool.requiredPermissions()).containsExactlyInAnyOrder("approval:act", "todo:read");
        var args = json.createObjectNode().put("taskId", 1).put("targetUserId", 2).put("version", 0).put("mode", "PRE").put("reason", "Additional reviewer");
        assertThat(validator.valid(tool.inputSchema(), args)).isTrue();
        for (String authority : java.util.List.of("tenantId", "userId", "parentTaskId", "url")) {
            var altered = args.deepCopy().put(authority, 9);
            assertThat(validator.valid(tool.inputSchema(), altered)).as(authority).isFalse();
        }
        assertThat(validator.valid(tool.inputSchema(), args.deepCopy().put("mode", "PARALLEL"))).isFalse();
        assertThat(validator.valid(tool.inputSchema(), args.deepCopy().put("version", Integer.MAX_VALUE))).isFalse();
        assertThat(validator.valid(tool.inputSchema(), args.deepCopy().put("reason", ""))).isFalse();
    }
    @Test void requiresTodoNavigationEvenFromWorkspace() {
        var pages = new com.aiworkmate.agent.capability.PageCapabilityCatalog();
        assertThat(pages.allowsTool("ai-workspace", "approval.genericTask.addSign", java.util.Set.of("route:ai-workspace"))).isFalse();
        assertThat(pages.allowsTool("ai-workspace", "approval.genericTask.addSign", java.util.Set.of("route:ai-workspace", "route:todo"))).isTrue();
        assertThat(pages.allowsTool("meeting-room", "approval.genericTask.addSign", java.util.Set.of("route:meeting-room", "route:todo"))).isFalse();
    }
    @Test void genericAddSignIsAnExplicitSingleWriteCapability(){
        assertThat(ToolCode.isSupported("approval.genericTask.addSign")).isTrue();
        assertThat(ToolCode.fromCode("approval.genericTask.addSign").sideEffect()).isEqualTo(SideEffect.SINGLE_WRITE);
    }
}
