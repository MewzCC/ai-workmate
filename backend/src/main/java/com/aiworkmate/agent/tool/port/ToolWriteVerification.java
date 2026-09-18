package com.aiworkmate.agent.tool.port;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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

    /**
     * Converts a domain lookup into the transport-neutral verification result.
     * The mapper is only invoked for an observed domain result, so local and
     * future remote adapters share the same non-retry semantics.
     */
    public static <S, R extends ToolWriteReceipt> ToolWriteVerification<R> fromOptional(
            Optional<S> source, Function<S, R> receiptMapper) {
        Objects.requireNonNull(source, "Verification source is required");
        Objects.requireNonNull(receiptMapper, "Receipt mapper is required");
        return source.map(receiptMapper)
                .map(ToolWriteVerification::observed)
                .orElseGet(ToolWriteVerification::unobserved);
    }

    public boolean observed() {
        return status == Status.OBSERVED;
    }
}
