package com.aiworkmate.service.model;

import java.time.LocalDateTime;

public record EmployeeChangeAgentApplicationReceipt(
        long changeId, String status, int version, LocalDateTime submittedAt) {
}
