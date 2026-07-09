package com.aiworkmate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求 — 通过邮箱验证码重置
 */
@Data
public class ResetPasswordRequest {

    @NotBlank(message = "邮箱不能为空")
    private String email;

    @NotBlank(message = "邮箱验证码不能为空")
    private String emailCode;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度6-100位")
    private String newPassword;
}
