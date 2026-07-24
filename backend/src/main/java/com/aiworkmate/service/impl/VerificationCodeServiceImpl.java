package com.aiworkmate.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.AuthProperties;
import com.aiworkmate.dto.CaptchaResponse;
import com.aiworkmate.dto.CodeScene;
import com.aiworkmate.dto.EmailCodeRequest;
import com.aiworkmate.service.MailDeliveryService;
import com.aiworkmate.service.VerificationCodeService;
import com.wf.captcha.SpecCaptcha;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final String CAPTCHA_PREFIX = "oa:captcha:image:";
    private static final String EMAIL_PREFIX = "oa:captcha:email:";
    private static final String COOLDOWN_PREFIX = "oa:captcha:cooldown:";
    private static final String HOURLY_PREFIX = "oa:captcha:hourly:";
    private static final String DAILY_PREFIX = "oa:captcha:daily:";
    private static final String IP_PREFIX = "oa:captcha:ip:";
    private static final int IP_MINUTE_LIMIT = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('HGET', KEYS[1], 'codeHash')
            if not stored then return -1 end
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
            if stored ~= ARGV[1] then
              attempts = attempts + 1
              if attempts >= tonumber(ARGV[2]) then redis.call('DEL', KEYS[1])
              else redis.call('HSET', KEYS[1], 'attempts', attempts) end
              return 0
            end
            redis.call('DEL', KEYS[1])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> SEND_LIMIT_SCRIPT = new DefaultRedisScript<>("""
            local cooldownTtl = redis.call('TTL', KEYS[1])
            if cooldownTtl > 0 then return cooldownTtl end
            local hourly = tonumber(redis.call('GET', KEYS[2]) or '0')
            if hourly >= tonumber(ARGV[1]) then return redis.call('TTL', KEYS[2]) end
            local daily = tonumber(redis.call('GET', KEYS[3]) or '0')
            if daily >= tonumber(ARGV[2]) then return redis.call('TTL', KEYS[3]) end
            local ipCount = tonumber(redis.call('GET', KEYS[4]) or '0')
            if ipCount >= tonumber(ARGV[3]) then return redis.call('TTL', KEYS[4]) end
            redis.call('SET', KEYS[1], '1', 'EX', ARGV[4], 'NX')
            local h = redis.call('INCR', KEYS[2]); if h == 1 then redis.call('EXPIRE', KEYS[2], 3600) end
            local d = redis.call('INCR', KEYS[3]); if d == 1 then redis.call('EXPIRE', KEYS[3], 172800) end
            local i = redis.call('INCR', KEYS[4]); if i == 1 then redis.call('EXPIRE', KEYS[4], 60) end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> ROLLBACK_FAILED_SEND_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1], KEYS[2])
            for index = 3, 5 do
              local current = tonumber(redis.call('GET', KEYS[index]) or '0')
              if current <= 1 then redis.call('DEL', KEYS[index])
              else redis.call('DECR', KEYS[index]) end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final MailDeliveryService mailDeliveryService;
    private final AuthProperties properties;

    @Value("${jwt.secret}")
    private String hashSecret;

    @Override
    public CaptchaResponse createImageCaptcha() {
        SpecCaptcha captcha = new SpecCaptcha(132, 44, 4);
        String captchaId = IdUtil.fastSimpleUUID();
        String key = CAPTCHA_PREFIX + captchaId;
        Map<String, String> value = Map.of(
                "codeHash", hash(captcha.text().toLowerCase()),
                "scene", "image",
                "createdAt", LocalDateTime.now().toString(),
                "verified", "false",
                "attempts", "0"
        );
        withRedis(() -> {
            redisTemplate.opsForHash().putAll(key, value);
            redisTemplate.expire(key, properties.getCaptchaTtl(), TimeUnit.SECONDS);
        });
        return new CaptchaResponse(captchaId, captcha.toBase64(), properties.getCaptchaTtl());
    }

    @Override
    public void sendEmailCode(EmailCodeRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        verifyImageCaptcha(request.captchaId(), request.captchaCode());
        SendQuotaReservation reservation = reserveSendQuota(request.scene(), email, clientIp);

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String key = emailKey(request.scene(), email);
        Map<String, String> value = Map.of(
                "codeHash", hash(code),
                "scene", request.scene().value(),
                "email", email,
                "createdAt", LocalDateTime.now().toString(),
                "verified", "false",
                "attempts", "0"
        );
        withRedis(() -> {
            redisTemplate.opsForHash().putAll(key, value);
            redisTemplate.expire(key, properties.getEmailCodeTtl(), TimeUnit.SECONDS);
        });
        try {
            mailDeliveryService.sendVerificationCode(email, request.scene(), code, properties.getEmailCodeTtl());
        } catch (RuntimeException ex) {
            rollbackFailedSend(key, reservation, ex);
            throw ex;
        }
    }

    @Override
    public void verifyEmailCode(CodeScene scene, String email, String code) {
        verify(emailKey(scene, normalizeEmail(email)), code, properties.getEmailCodeMaxAttempts(), "邮箱验证码");
    }

    @Override
    public void verifyImageCaptcha(String captchaId, String code) {
        if (captchaId == null || captchaId.isBlank() || code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_CAPTCHA_REQUIRED);
        }
        verify(CAPTCHA_PREFIX + captchaId, code.toLowerCase(), properties.getEmailCodeMaxAttempts(), "图形验证码");
    }

    private void verify(String key, String code, int maxAttempts, String label) {
        Long result = withRedisResult(() -> redisTemplate.execute(
                VERIFY_SCRIPT, List.of(key), hash(code), String.valueOf(maxAttempts)));
        if (result == null || result == -1) {
            throw new BusinessException(ErrorCode.AUTH_CODE_EXPIRED, label + "已过期，请重新获取");
        }
        if (result == 0) {
            throw new BusinessException(ErrorCode.AUTH_CODE_INVALID, label + "错误或尝试次数过多");
        }
    }

    private SendQuotaReservation reserveSendQuota(CodeScene scene, String email, String clientIp) {
        String scope = scene.value() + ":" + email;
        List<String> keys = List.of(
                COOLDOWN_PREFIX + scope,
                HOURLY_PREFIX + scope + ":" + LocalDateTime.now().format(HOUR_FORMAT),
                DAILY_PREFIX + scope + ":" + LocalDate.now().format(DAY_FORMAT),
                IP_PREFIX + scene.value() + ":" + keyPart(clientIp)
        );
        Long retryAfter = withRedisResult(() -> redisTemplate.execute(
                SEND_LIMIT_SCRIPT,
                keys,
                String.valueOf(properties.getEmailCodeHourlyLimit()),
                String.valueOf(properties.getEmailCodeDailyLimit()),
                String.valueOf(IP_MINUTE_LIMIT),
                String.valueOf(properties.getEmailCodeCooldown())
        ));
        if (retryAfter != null && retryAfter > 0) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "请求过于频繁，请在 " + retryAfter + " 秒后重试");
        }
        return new SendQuotaReservation(keys);
    }

    private void rollbackFailedSend(String emailCodeKey, SendQuotaReservation reservation, RuntimeException sendFailure) {
        List<String> keys = new java.util.ArrayList<>(reservation.keys().size() + 1);
        keys.add(emailCodeKey);
        keys.addAll(reservation.keys());
        try {
            withRedisResult(() -> redisTemplate.execute(ROLLBACK_FAILED_SEND_SCRIPT, keys));
        } catch (BusinessException cleanupFailure) {
            sendFailure.addSuppressed(cleanupFailure);
            log.error("Failed to release email verification quota after delivery failure", cleanupFailure);
        }
    }

    private String emailKey(CodeScene scene, String email) {
        return EMAIL_PREFIX + scene.value() + ":" + email;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String keyPart(String input) {
        return hash(input == null ? "unknown" : input).substring(0, 24);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((hashSecret + ":" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void withRedis(Runnable action) {
        try {
            action.run();
        } catch (DataAccessException ex) {
            log.error("Redis verification state is unavailable", ex);
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "认证服务暂时不可用，请稍后重试");
        }
    }

    private <T> T withRedisResult(RedisSupplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException ex) {
            log.error("Redis verification state is unavailable", ex);
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "认证服务暂时不可用，请稍后重试");
        }
    }

    @FunctionalInterface
    private interface RedisSupplier<T> {
        T get();
    }

    private record SendQuotaReservation(List<String> keys) {
        private SendQuotaReservation {
            keys = List.copyOf(keys);
        }
    }
}
