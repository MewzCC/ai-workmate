package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;

/** Stable across worker attempts; not an execution permission or resource ownership proof. */
final class StableToolOperationKey {
    private StableToolOperationKey() {}

    static String v1(TrustedToolContext context, ToolCode code) {
        if (context == null || context.taskId() < 1 || context.stepId() < 1 || code == null) {
            throw new IllegalArgumentException("Trusted task, step and tool are required");
        }
        return "agent:" + context.taskId() + ":" + context.stepId() + ":" + code.code() + ":v1";
    }
}
