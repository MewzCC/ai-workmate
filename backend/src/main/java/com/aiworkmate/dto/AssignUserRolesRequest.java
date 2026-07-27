package com.aiworkmate.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Set;

public record AssignUserRolesRequest(
        @NotEmpty Set<
                @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{1,39}") String> roleCodes
) {
}
