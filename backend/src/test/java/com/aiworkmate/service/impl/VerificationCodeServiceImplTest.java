package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.AuthProperties;
import com.aiworkmate.dto.CodeScene;
import com.aiworkmate.service.MailDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private VerificationCodeServiceImpl service;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        service = new VerificationCodeServiceImpl(redisTemplate, mock(MailDeliveryService.class), new AuthProperties());
        ReflectionTestUtils.setField(service, "hashSecret", "test-secret-that-is-long-enough");
    }

    @Test
    void shouldConsumeValidEmailCode() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any())).thenReturn(1L);
        service.verifyEmailCode(CodeScene.LOGIN, "user@example.com", "123456");
    }

    @Test
    void shouldRejectExpiredEmailCode() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any())).thenReturn(-1L);
        assertThatThrownBy(() -> service.verifyEmailCode(CodeScene.REGISTER, "user@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已过期");
    }

    @Test
    void shouldInvalidateCodeAfterTooManyWrongAttempts() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any())).thenReturn(0L);
        assertThatThrownBy(() -> service.verifyImageCaptcha("captcha-id", "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("错误");
    }

    @Test
    void shouldReleaseQuotaWhenEmailDeliveryFails() {
        MailDeliveryService mailDeliveryService = mock(MailDeliveryService.class);
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        VerificationCodeServiceImpl failingService = new VerificationCodeServiceImpl(
                redisTemplate, mailDeliveryService, new AuthProperties());
        ReflectionTestUtils.setField(failingService, "hashSecret", "test-secret-that-is-long-enough");

        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(0L);
        doThrow(new BusinessException(com.aiworkmate.common.ErrorCode.AUTH_SERVICE_UNAVAILABLE))
                .when(mailDeliveryService).sendVerificationCode(any(), any(), any(), any(Long.class));

        assertThatThrownBy(() -> failingService.sendEmailCode(
                new com.aiworkmate.dto.EmailCodeRequest(
                        "user@example.com", CodeScene.REGISTER, "captcha-id", "abcd"),
                "127.0.0.1"))
                .isInstanceOf(BusinessException.class);

        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                org.mockito.ArgumentMatchers.argThat(keys -> keys.size() == 5));
    }
}
