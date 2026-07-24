package com.aiworkmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordLoginRequest(
        @NotBlank(message = "企业邮箱不能为空") @Email(message = "企业邮箱格式不正确") String email,
        @NotBlank(message = "登录密码不能为空") @Size(max = 128, message = "登录密码格式不正确") String password,
        String captchaId,
        String captchaCode,
        boolean remember) {
}
