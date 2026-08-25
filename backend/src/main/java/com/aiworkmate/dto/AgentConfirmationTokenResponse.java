package com.aiworkmate.dto;

import java.time.OffsetDateTime;

public record AgentConfirmationTokenResponse(String confirmationToken, OffsetDateTime expiresAt) { }
