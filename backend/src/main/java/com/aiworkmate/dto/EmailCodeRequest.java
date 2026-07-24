package com.aiworkmate.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EmailCodeRequest(
        @NotBlank(message = "企业邮箱不能为空") @Email(message = "企业邮箱格式不正确")
        @Size(max = 100, message = "企业邮箱不能超过 100 个字符") String email,
        @NotNull(message = "验证码场景不能为空") CodeScene scene,
        @NotBlank(message = "图形验证码 ID 不能为空") String captchaId,
        @NotBlank(message = "图形验证码不能为空") @Size(max = 8, message = "图形验证码格式不正确") String captchaCode) {
}
