package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.AuthProperties;
import com.aiworkmate.service.LoginProtectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginProtectionServiceImpl implements LoginProtectionService {

    private static final String FAIL_PREFIX = "oa:login:fail:";
    private static final String LOCK_PREFIX = "oa:login:lock:";
    private static final String REGISTER_PREFIX = "oa:register:idempotent:";
    private static final int CAPTCHA_FAILURE_THRESHOLD = 3;

    private static final DefaultRedisScript<Long> FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[2]) end
            if count >= tonumber(ARGV[1]) then
              redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
              redis.call('DEL', KEYS[1])
              return -1
            end
            return count
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    @Override
    public void assertLoginAllowed(String account, String clientIp) {
        Long ttl = redis(() -> redisTemplate.getExpire(lockKey(account, clientIp), TimeUnit.SECONDS));
        if (ttl != null && ttl > 0) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED,
                    "登录尝试过多，请在 " + ttl + " 秒后重试");
        }
    }

    @Override
    public boolean isCaptchaRequired(String account, String clientIp) {
        String count = redis(() -> redisTemplate.opsForValue().get(failKey(account, clientIp)));
        return count != null && Integer.parseInt(count) >= CAPTCHA_FAILURE_THRESHOLD;
    }

    @Override
    public void recordFailure(String account, String clientIp) {
        Long result = redis(() -> redisTemplate.execute(
                FAILURE_SCRIPT,
                List.of(failKey(account, clientIp), lockKey(account, clientIp)),
                String.valueOf(properties.getLoginMaxFailures()),
                String.valueOf(properties.getLoginLockSeconds())
        ));
        log.warn("Password login failed, accountDomain={}, clientIp={}", domainOf(account), clientIp);
        if (result != null && result == -1) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_LOCKED,
                    "登录尝试过多，账号与当前网络已临时锁定");
        }
    }

    @Override
    public void clearFailures(String account, String clientIp) {
        redis(() -> redisTemplate.delete(List.of(failKey(account, clientIp), lockKey(account, clientIp))));
    }

    @Override
    public void claimRegistration(String requestId) {
        Boolean claimed = redis(() -> redisTemplate.opsForValue().setIfAbsent(
                REGISTER_PREFIX + requestId, "1", 10, TimeUnit.MINUTES));
        if (!Boolean.TRUE.equals(claimed)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "请勿重复提交注册申请");
        }
    }

    private String failKey(String account, String clientIp) {
        return FAIL_PREFIX + normalize(account) + ":" + normalize(clientIp);
    }

    private String lockKey(String account, String clientIp) {
        return LOCK_PREFIX + normalize(account) + ":" + normalize(clientIp);
    }

    private String normalize(String value) {
        return value == null ? "unknown" : value.trim().toLowerCase().replace(':', '_');
    }

    private String domainOf(String email) {
        int index = email.indexOf('@');
        return index < 0 ? "invalid" : email.substring(index + 1);
    }

    private <T> T redis(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException ex) {
            log.error("Redis login protection state is unavailable", ex);
            throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE, "认证服务暂时不可用，请稍后重试");
        }
    }
}
