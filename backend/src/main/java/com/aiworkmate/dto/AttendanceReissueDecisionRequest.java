package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 补卡审批决定。
 *
 * <p>{@code APPROVED} 通过；{@code REJECTED} 驳回。
 */
public record AttendanceReissueDecisionRequest(
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED", message = "{validation.attendance.decision.invalid}")
        String decision,
        @Size(max = 500) String comment
) {
}
