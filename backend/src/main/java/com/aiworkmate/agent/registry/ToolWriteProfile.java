package com.aiworkmate.agent.registry;

/**
 * Code-owned write execution profiles. Tool definitions select one reviewed
 * combination instead of assembling risk, retry and confirmation policies independently.
 */
public enum ToolWriteProfile {
    IDEMPOTENT_L1(RiskLevel.L1, RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT),
    NON_RETRYABLE_L1(RiskLevel.L1, RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT),
    SECONDARY_L2(RiskLevel.L2, RetryPolicy.NEVER, ConfirmationPolicy.SECONDARY);

    private final RiskLevel riskLevel;
    private final RetryPolicy retryPolicy;
    private final ConfirmationPolicy confirmationPolicy;

    ToolWriteProfile(
            RiskLevel riskLevel,
            RetryPolicy retryPolicy,
            ConfirmationPolicy confirmationPolicy) {
        this.riskLevel = riskLevel;
        this.retryPolicy = retryPolicy;
        this.confirmationPolicy = confirmationPolicy;
    }

    public RiskLevel riskLevel() {
        return riskLevel;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public ConfirmationPolicy confirmationPolicy() {
        return confirmationPolicy;
    }
}
