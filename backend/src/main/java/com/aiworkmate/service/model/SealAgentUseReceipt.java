package com.aiworkmate.service.model;

import java.time.LocalDateTime;

/** Immutable receipt used to verify one Agent actual seal-use registration. */
public record SealAgentUseReceipt(
        long usageId, String status, int version, int actualCopies, LocalDateTime usedAt) {
}
