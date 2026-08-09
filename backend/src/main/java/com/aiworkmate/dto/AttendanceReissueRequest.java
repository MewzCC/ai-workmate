package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 补卡申请请求。
 */
public record AttendanceReissueRequest(
        @NotNull LocalDate clockDate,
        @NotBlank
        @Pattern(regexp = "CLOCK_IN|CLOCK_OUT", message = "{validation.attendance.clockType.invalid}")
        String clockType,
        @NotBlank @Size(max = 500) String reason
) {
}
