package com.aiworkmate.service.model;

import java.time.LocalDateTime;

public record VisitorAgentApplicationCommand(
        String visitorName, String visitorCompany, String visitorPhone, String purpose,
        long hostUserId, LocalDateTime expectedVisitAt, LocalDateTime expectedLeaveAt,
        String plateNumber, int partySize) {
}
