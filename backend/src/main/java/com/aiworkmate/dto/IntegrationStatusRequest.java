package com.aiworkmate.dto;
import jakarta.validation.constraints.*;
public record IntegrationStatusRequest(@NotBlank @Pattern(regexp="ACTIVE|DISABLED") String status,
                                       @NotNull Integer version) {}
