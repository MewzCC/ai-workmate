package com.aiworkmate.dto;

public record ApproverCandidateResponse(
        Long id,
        String name,
        String departmentName,
        String positionName,
        boolean recommended
) {
}
