package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SaveDepartmentRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_-]{1,59}") String code,
        @NotBlank @Size(max = 100) String name,
        Long parentId,
        Long defaultApproverUserId
) {
}
