package com.aiworkmate.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.CaptchaResponse;
import com.aiworkmate.dto.SendCodeRequest;
import com.wf.captcha.SpecCaptcha;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 验证码控制器 — 图形验证码 + 邮件验证码
 *
 * 流程参考大型开源项目：
 * 1. GET  /api/auth/captcha        → 生成图形验证码，返回 captchaId + base64 图片
 * 2. POST /api/auth/send-code      → 校验图形验证码 → 发送邮件验证码到邮箱
 * 3. POST /api/auth/register|login → 校验邮件验证码 + 业务逻辑
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class CaptchaController {

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:your-email@qq.com}")
    private String mailFrom;

    // ========== Redis Key 设计 ==========
    // 图形验证码：  captcha:img:{captchaId}  → code（5 分钟过期）
    // 邮件验证码：  captcha:email:{email}    → code（5 分钟过期）
    // 邮件发送频率： captcha:email:lock:{email} → 1（60 秒过期，防刷）

    private static final String CAPTCHA_IMG_KEY = "captcha:img:";
    private static final String EMAIL_CODE_KEY = "captcha:email:";
    private static final String EMAIL_LOCK_KEY = "captcha:email:lock:";
    private static final long CAPTCHA_EXPIRE_MINUTES = 5;
    private static final long EMAIL_EXPIRE_MINUTES = 5;
    private static final long EMAIL_LOCK_SECONDS = 60;

    /**
     * 生成图形验证码 — 返回 Base64 图片 + 唯一 captchaId
     */
    @GetMapping("/captcha")
    public Result<CaptchaResponse> captcha() {
        SpecCaptcha specCaptcha = new SpecCaptcha(130, 38, 4);
        String code = specCaptcha.text().toLowerCase();
        String captchaId = IdUtil.fastSimpleUUID();

        // 存入 Redis，5 分钟过期
        redisTemplate.opsForValue().set(
                CAPTCHA_IMG_KEY + captchaId, code,
                CAPTCHA_EXPIRE_MINUTES, TimeUnit.MINUTES
        );

        String base64 = specCaptcha.toBase64();

        CaptchaResponse resp = new CaptchaResponse();
        resp.setCaptchaId(captchaId);
        resp.setCaptchaImage(base64);
        return Result.ok(resp);
    }

    /**
     * 发送邮件验证码 — 先校验图形验证码，再发邮件
     */
    @PostMapping("/send-code")
    public Result<Void> sendEmailCode(@Valid @RequestBody SendCodeRequest request) {
        // 1. 校验图形验证码
        String imgKey = CAPTCHA_IMG_KEY + request.getCaptchaId();
        String storedCode = redisTemplate.opsForValue().get(imgKey);

        if (StrUtil.isEmpty(storedCode)) {
            return Result.error(400, "图形验证码已过期，请刷新");
        }
        if (!storedCode.equalsIgnoreCase(request.getCaptchaCode())) {
            return Result.error(400, "图形验证码错误");
        }
        // 校验通过后删除图形验证码（防止重放）
        redisTemplate.delete(imgKey);

        // 2. 频率限制 — 60 秒内同一邮箱只能发一次
        String lockKey = EMAIL_LOCK_KEY + request.getEmail();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            return Result.error(429, "验证码发送过于频繁，请60秒后重试");
        }

        // 3. 生成 6 位数字验证码
        String emailCode = String.format("%06d", (int) (Math.random() * 1000000));

        // 4. 存入 Redis
        redisTemplate.opsForValue().set(
                EMAIL_CODE_KEY + request.getEmail(), emailCode,
                EMAIL_EXPIRE_MINUTES, TimeUnit.MINUTES
        );
        redisTemplate.opsForValue().set(
                lockKey, "1",
                EMAIL_LOCK_SECONDS, TimeUnit.SECONDS
        );

        // 5. 异步发送邮件
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
            message.setTo(request.getEmail());
            message.setSubject("AI WorkMate — 邮箱验证码");
            message.setText("您的验证码是：" + emailCode + "\n\n验证码5分钟内有效，请勿泄露给他人。\n\nAI WorkMate 团队");
            mailSender.send(message);
            log.info("Email code sent to {}", request.getEmail());
        } catch (Exception e) {
            log.error("Failed to send email to {}", request.getEmail(), e);
            // 邮件发送失败时清除验证码，避免无效验证码残留
            redisTemplate.delete(EMAIL_CODE_KEY + request.getEmail());
            redisTemplate.delete(lockKey);
            return Result.error(500, "邮件发送失败，请检查邮箱地址或稍后重试");
        }

        return Result.ok();
    }
}
