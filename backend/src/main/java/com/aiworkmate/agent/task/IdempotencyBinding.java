package com.aiworkmate.agent.task;

public record IdempotencyBinding(boolean created, Long taskId) {
}
