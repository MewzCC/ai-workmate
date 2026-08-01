package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record ApproverCandidateResponse(
        Long id,
        String name,
        String departmentName,
        String positionName,
        boolean recommended,
        String avatar,
        LocalDateTime updatedAt,
        String avatarUrl
) {
}
