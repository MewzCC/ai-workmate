package com.aiworkmate.agent.task;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Idempotency key was already used with a different request");
    }
}
