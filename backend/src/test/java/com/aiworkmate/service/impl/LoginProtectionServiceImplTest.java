package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.AuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginProtectionServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private LoginProtectionServiceImpl service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new LoginProtectionServiceImpl(redisTemplate, new AuthProperties());
    }

    @Test
    void shouldRequireCaptchaAfterThreeFailures() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn("3");
        assertThat(service.isCaptchaRequired("user@example.com", "127.0.0.1")).isTrue();
    }

    @Test
    void shouldRejectLockedLoginWithRemainingSeconds() {
        when(redisTemplate.getExpire(any(), any(TimeUnit.class))).thenReturn(420L);
        assertThatThrownBy(() -> service.assertLoginAllowed("user@example.com", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("420 秒");
    }

    @Test
    void shouldLockAfterMaximumFailures() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any())).thenReturn(-1L);
        assertThatThrownBy(() -> service.recordFailure("user@example.com", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("临时锁定");
    }
}
