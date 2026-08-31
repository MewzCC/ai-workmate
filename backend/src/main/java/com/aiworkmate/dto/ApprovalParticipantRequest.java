package com.aiworkmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 审批转交或抄送请求；version 对应当前待办版本。 */
public record ApprovalParticipantRequest(
        @NotNull Long targetUserId,
        @NotNull @Min(0) Integer version,
        @NotBlank @Size(max = 500) String reason
) {
}
