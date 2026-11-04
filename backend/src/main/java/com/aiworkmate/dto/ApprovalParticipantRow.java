package com.aiworkmate.dto;

import java.time.LocalDateTime;

/** Internal projection; the domain converts avatar references to protected URLs. */
public record ApprovalParticipantRow(Long id, String name, String avatar, LocalDateTime updatedAt,
                                     boolean canApprove) { }
