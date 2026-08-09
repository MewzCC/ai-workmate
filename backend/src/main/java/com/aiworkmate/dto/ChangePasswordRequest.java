package com.aiworkmate.dto;

import com.aiworkmate.common.validation.PasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 已登录用户修改密码请求。
 *
 * <p>仅校验字段格式与策略；旧密码正确性、新旧密码是否相同由 service 层结合数据库哈希判断。
 * confirmPassword 在前端表单内校验，不传后端，保持 DTO 精简（与 ResetPasswordRequest 一致）。
 */
public record ChangePasswordRequest(
        @NotBlank(message = "{validation.oldPassword.notBlank}") String oldPassword,
        @NotBlank(message = "{validation.newPassword.notBlank}")
        @Size(min = 8, max = 32, message = "{validation.password.length}")
        @Pattern(regexp = PasswordPolicy.PASSWORD_REGEX, message = PasswordPolicy.PASSWORD_MESSAGE) String newPassword) {
}
