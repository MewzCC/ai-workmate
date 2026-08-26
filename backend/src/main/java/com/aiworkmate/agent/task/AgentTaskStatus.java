package com.aiworkmate.agent.task;

public enum AgentTaskStatus {
    RECEIVED,
    PLANNING,
    PLAN_READY,
    WAITING_CONFIRMATION,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    PARTIALLY_SUCCEEDED,
    FAILED,
    TIMED_OUT,
    REJECTED,
    EXPIRED,
    CANCELLED;

    public boolean terminal() {
        return switch (this) {
            case SUCCEEDED, PARTIALLY_SUCCEEDED, FAILED, TIMED_OUT, REJECTED, EXPIRED, CANCELLED -> true;
            default -> false;
        };
    }
}
