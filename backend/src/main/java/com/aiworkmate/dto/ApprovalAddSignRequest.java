package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 审批加签请求；mode 仅允许 PRE（前加签）或 POST（后加签）。 */
public record ApprovalAddSignRequest(
        @NotNull Long targetUserId,
        @NotNull @Min(0) Integer version,
        @NotBlank @Pattern(regexp = "PRE|POST") String mode,
        @NotBlank @Size(max = 500) String reason
) {
}
