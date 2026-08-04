package com.aiworkmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordLoginRequest(
        @NotBlank(message = "{validation.email.notBlank}") @Email(message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.password.notBlank}") @Size(max = 128, message = "{validation.password.format}") String password,
        String captchaId,
        String captchaCode,
        boolean remember) {
}
