package com.aiworkmate.service.model;

import java.time.LocalDateTime;

/** Immutable receipt used to verify an Agent visitor lifecycle transition. */
public record VisitorAgentVisitReceipt(
        long bookingId, String status, int version, LocalDateTime occurredAt) {
}
