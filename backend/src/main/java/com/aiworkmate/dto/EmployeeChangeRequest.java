package com.aiworkmate.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EmployeeChangeRequest(
        @NotNull Long employeeUserId,
        @NotBlank @Pattern(regexp = "ONBOARDING|REGULARIZATION|TRANSFER|OFFBOARDING") String changeType,
        @NotNull @FutureOrPresent LocalDate effectiveDate,
        Long targetDepartmentId,
        Long targetPositionId,
        Long targetSupervisorUserId,
        @NotNull Long reviewApproverUserId,
        @NotBlank @Size(max = 1000) String reason
) {
}
