package com.aiworkmate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.Set;

public record UpdateRoleMembersRequest(
        @NotNull Set<@Positive Long> userIds
) {
}
