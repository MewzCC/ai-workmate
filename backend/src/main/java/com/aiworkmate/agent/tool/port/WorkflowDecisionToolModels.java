package com.aiworkmate.agent.tool.port;

/** Bounded decision coordinates shared by single-node business workflows. Versions are deliberately separate. */
public final class WorkflowDecisionToolModels {
    private WorkflowDecisionToolModels() { }
    public record Command(long taskId, int version, String comment) { }
    public record Receipt(long resourceId, String resourceType, String resourceStatus, int resourceVersion,
                          long taskId, String status, int version, String action) implements ToolWriteReceipt { }
}
