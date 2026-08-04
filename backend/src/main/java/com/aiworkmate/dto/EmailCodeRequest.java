package com.aiworkmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmailCodeRequest(
        @NotBlank(message = "{validation.email.notBlank}") @Email(message = "{validation.email.invalid}")
        @Size(max = 100, message = "{validation.email.maxLength}") String email,
        @NotNull(message = "{validation.codeScene.notNull}") CodeScene scene,
        @NotBlank(message = "{validation.captchaId.notBlank}") String captchaId,
        @NotBlank(message = "{validation.captchaCode.notBlank}") @Size(max = 8, message = "{validation.captchaCode.format}") String captchaCode) {
}
