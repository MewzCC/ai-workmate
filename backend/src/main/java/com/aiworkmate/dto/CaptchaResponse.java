package com.aiworkmate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图形验证码 + 邮件验证码响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaResponse {
    private String captchaId;    // 图形验证码唯一 ID（前端提交时带回）
    private String captchaImage; // Base64 编码的验证码图片
}
