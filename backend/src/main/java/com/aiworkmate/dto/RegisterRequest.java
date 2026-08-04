package com.aiworkmate.dto;

import com.aiworkmate.common.validation.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "{validation.name.notBlank}") @Size(max = 50, message = "{validation.name.maxLength}") String name,
        @NotBlank(message = "{validation.email.notBlank}") @Email(message = "{validation.email.invalid}") String email,
        @NotBlank(message = "{validation.emailCode.notBlank}")
        @Pattern(regexp = "^\\d{6}$", message = "{validation.emailCode.pattern}") String emailCode,
        @NotBlank(message = "{validation.newPassword.notBlank}")
        @Size(min = 8, max = 32, message = "{validation.password.length}")
        @Pattern(regexp = PasswordPolicy.PASSWORD_REGEX, message = PasswordPolicy.PASSWORD_MESSAGE) String password,
        @AssertTrue(message = "{validation.agreement.assertTrue}") boolean agreement,
        @NotBlank(message = "{validation.requestId.notBlank}")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "{validation.requestId.pattern}") String requestId) {
}
