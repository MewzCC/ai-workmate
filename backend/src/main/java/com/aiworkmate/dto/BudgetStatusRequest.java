package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record BudgetStatusRequest(@NotBlank @Pattern(regexp="ACTIVE|CLOSED|CANCELLED") String status,
                                  @Size(max=500) String reason, @NotNull Integer version) {}
