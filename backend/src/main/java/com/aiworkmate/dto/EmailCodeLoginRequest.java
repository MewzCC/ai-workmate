package com.aiworkmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailCodeLoginRequest(
        @NotBlank(message = "{validation.email.notBlank}") @Email(message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.emailCode.notBlank}")
        @Pattern(regexp = "^\\d{6}$", message = "{validation.emailCode.pattern}") String emailCode,
        boolean remember) {
}
