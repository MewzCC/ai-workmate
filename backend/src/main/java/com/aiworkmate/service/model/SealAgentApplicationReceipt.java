package com.aiworkmate.service.model;

import java.time.LocalDateTime;

public record SealAgentApplicationReceipt(
        long usageId, String status, int version, LocalDateTime submittedAt) {
}
