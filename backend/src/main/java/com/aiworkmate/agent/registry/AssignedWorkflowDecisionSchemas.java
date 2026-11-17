package com.aiworkmate.agent.registry;

/** Fixed schemas for approved/rejected single-node workflows; never accepts a model-selected business type. */
public final class AssignedWorkflowDecisionSchemas {
    private AssignedWorkflowDecisionSchemas() { }
    public static final String APPROVE_INPUT = AgentApprovalTaskWriteToolDefinitions.DECISION_INPUT_SCHEMA;
    public static final String REJECT_INPUT = AgentGenericApprovalTaskRejectToolDefinition.INPUT_SCHEMA;

    public static String output(String resourceType, boolean approved) {
        if (!java.util.Set.of("VISITOR_BOOKING", "SEAL_USAGE").contains(resourceType))
            throw new IllegalArgumentException("Unsupported assigned workflow");
        String status=approved?"APPROVED":"REJECTED",action=approved?"APPROVE":"REJECT";
        return """
                {"type":"object","additionalProperties":false,"required":["resourceId","resourceType","resourceStatus","resourceVersion","taskId","status","version","action"],"properties":{"resourceId":{"type":"integer","minimum":1},"resourceType":{"type":"string","enum":["%s"]},"resourceStatus":{"type":"string","enum":["%s"]},"resourceVersion":{"type":"integer","minimum":1,"maximum":2147483647},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["%s"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"action":{"type":"string","enum":["%s"]}}}
                """.formatted(resourceType,status,status,action).strip();
    }
}
