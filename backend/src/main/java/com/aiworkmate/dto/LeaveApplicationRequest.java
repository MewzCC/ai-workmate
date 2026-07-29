package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record LeaveApplicationRequest(
        @NotBlank
        @Pattern(regexp = "ANNUAL|PERSONAL|SICK|MARRIAGE|MATERNITY|PATERNITY|BEREAVEMENT|COMPENSATORY|OTHER")
        String leaveType,
        Long approverUserId,
        @NotNull LocalDate startDate,
        @NotBlank @Pattern(regexp = "AM|PM") String startPeriod,
        @NotNull LocalDate endDate,
        @NotBlank @Pattern(regexp = "AM|PM") String endPeriod,
        @NotBlank @Size(max = 500) String reason,
        Integer version
) {
}
