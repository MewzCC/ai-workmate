package com.aiworkmate.service.model;

public record SealAgentApplicationCommand(
        String sealType, String documentTitle, String usageReason, int copies) {
}
