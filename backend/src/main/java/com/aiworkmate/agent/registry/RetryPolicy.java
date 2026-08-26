package com.aiworkmate.agent.registry;

public enum RetryPolicy {
    READ_ONLY_SAFE,
    BUSINESS_IDEMPOTENT,
    NEVER
}
