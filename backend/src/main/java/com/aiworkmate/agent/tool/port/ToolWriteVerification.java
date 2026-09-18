package com.aiworkmate.agent.tool.port;

/**
 * Explicit result of a read-only write-outcome verification.
 *
 * <p>{@link Status#UNOBSERVED} means that no committed result was observed at query time. It does
 * not prove that an in-flight write failed and must never authorize an automatic retry.</p>
 */
public record ToolWriteVerification<R extends ToolWriteReceipt>(Status status, R receipt) {
    public enum Status { OBSERVED, UNOBSERVED }

    public ToolWriteVerification {
        if (status == null) {
            throw new IllegalArgumentException("Write verification status is required");
        }
        if ((status == Status.OBSERVED) != (receipt != null)) {
            throw new IllegalArgumentException("Observed verification requires exactly one receipt");
        }
    }

    public static <R extends ToolWriteReceipt> ToolWriteVerification<R> observed(R receipt) {
        return new ToolWriteVerification<>(Status.OBSERVED, receipt);
    }

    public static <R extends ToolWriteReceipt> ToolWriteVerification<R> unobserved() {
        return new ToolWriteVerification<>(Status.UNOBSERVED, null);
    }

    public boolean observed() {
        return status == Status.OBSERVED;
    }
}
