package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserOrganizationRequest(
        @NotNull Long departmentId,
        @NotNull Long positionId,
        Long approverUserId
) {
}
