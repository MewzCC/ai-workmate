package com.aiworkmate.dto;

import com.aiworkmate.common.validation.PasswordPolicy;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "姓名不能为空") @Size(max = 50, message = "姓名不能超过 50 个字符") String name,
        @NotBlank(message = "企业邮箱不能为空") @Email(message = "企业邮箱格式不正确") String email,
        @NotBlank(message = "邮箱验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码必须为 6 位数字") String emailCode,
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 32, message = "密码长度必须为 8-32 位")
        @Pattern(regexp = PasswordPolicy.PASSWORD_REGEX, message = PasswordPolicy.PASSWORD_MESSAGE) String password,
        @AssertTrue(message = "请先同意服务协议和隐私政策") boolean agreement,
        @NotBlank(message = "请求标识不能为空")
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "请求标识格式不正确") String requestId) {
}
