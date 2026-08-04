package com.aiworkmate.dto;

import com.aiworkmate.common.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "{validation.email.notBlank}") @Email(message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.emailCode.notBlank}")
        @Pattern(regexp = "^\\d{6}$", message = "{validation.emailCode.pattern}") String emailCode,
        @NotBlank(message = "{validation.newPassword.notBlank}")
        @Size(min = 8, max = 32, message = "{validation.password.length}")
        @Pattern(regexp = PasswordPolicy.PASSWORD_REGEX, message = PasswordPolicy.PASSWORD_MESSAGE) String newPassword) {
}
