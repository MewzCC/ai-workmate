package com.aiworkmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailCodeLoginRequest(
        @NotBlank(message = "企业邮箱不能为空") @Email(message = "企业邮箱格式不正确") String email,
        @NotBlank(message = "邮箱验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "邮箱验证码必须为 6 位数字") String emailCode,
        boolean remember) {
}
