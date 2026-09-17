package com.aiworkmate.service.model;

import java.time.LocalDateTime;

public record VisitorAgentApplicationReceipt(
        long bookingId, String status, int version, LocalDateTime submittedAt) {
}
