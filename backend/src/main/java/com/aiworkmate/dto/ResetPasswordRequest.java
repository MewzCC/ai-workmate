package com.aiworkmate.dto;

import com.aiworkmate.common.validation.PasswordPolicy;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "企业邮箱不能为空") @Email(message = "企业邮箱格式不正确") String email,
        @NotBlank(message = "邮箱验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码必须为 6 位数字") String emailCode,
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度必须为 8-32 位")
        @Pattern(regexp = PasswordPolicy.PASSWORD_REGEX, message = PasswordPolicy.PASSWORD_MESSAGE) String newPassword) {
}
