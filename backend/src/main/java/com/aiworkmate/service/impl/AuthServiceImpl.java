package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.CodeScene;
import com.aiworkmate.dto.EmailCodeLoginRequest;
import com.aiworkmate.dto.PasswordLoginRequest;
import com.aiworkmate.dto.RegisterRequest;
import com.aiworkmate.dto.ResetPasswordRequest;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.AuthService;
import com.aiworkmate.service.LoginProtectionService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.VerificationCodeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private static final String INVALID_CREDENTIALS = "邮箱、密码或验证码错误";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final LoginProtectionService loginProtectionService;
    private final UserAccessService userAccessService;

    @Override
    public AuthUserResponse loginWithPassword(PasswordLoginRequest request, String clientIp) {
        String email = normalizeEmail(request.email());
        loginProtectionService.assertLoginAllowed(email, clientIp);
        if (loginProtectionService.isCaptchaRequired(email, clientIp)) {
            verificationCodeService.verifyImageCaptcha(request.captchaId(), request.captchaCode());
        }
        User user = findByEmail(email);
        if (!isActive(user) || !passwordEncoder.matches(request.password(), user.getPassword())) {
            loginProtectionService.recordFailure(email, clientIp);
            throw new BusinessException(ErrorCode.REQUEST_INVALID, INVALID_CREDENTIALS);
        }
        loginProtectionService.clearFailures(email, clientIp);
        log.info("Password login succeeded, userId={}", user.getId());
        return toResponse(user);
    }

    @Override
    public AuthUserResponse loginWithEmailCode(EmailCodeLoginRequest request) {
        String email = normalizeEmail(request.email());
        verificationCodeService.verifyEmailCode(CodeScene.LOGIN, email, request.emailCode());
        User user = findByEmail(email);
        if (!isActive(user)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, INVALID_CREDENTIALS);
        }
        log.info("Email code login succeeded, userId={}", user.getId());
        return toResponse(user);
    }

    @Override
    @Transactional
    public AuthUserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        verificationCodeService.verifyEmailCode(CodeScene.REGISTER, email, request.emailCode());
        loginProtectionService.claimRegistration(request.requestId());
        if (findByEmail(email) != null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "暂时无法创建账号，请检查信息后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(email);
        user.setDisplayName(request.name().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole("EMPLOYEE");
        user.setStatus(1);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);
        log.info("Enterprise account registered, userId={}", user.getId());
        return toResponse(user);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.email());
        verificationCodeService.verifyEmailCode(CodeScene.RESET_PASSWORD, email, request.emailCode());
        User user = findByEmail(email);
        if (user != null) {
            user.setPassword(passwordEncoder.encode(request.newPassword()));
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            log.info("Password reset succeeded, userId={}", user.getId());
        }
    }

    @Override
    public AuthUserResponse getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (!isActive(user)) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return toResponse(user);
    }

    private User findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    private boolean isActive(User user) {
        return user != null && Integer.valueOf(1).equals(user.getStatus());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private AuthUserResponse toResponse(User user) {
        String name = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
        String avatarUrl = user.getAvatar() == null || user.getAvatar().isBlank()
                ? null : "/api/profile/avatar/content?v=" + user.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return new AuthUserResponse(
                user.getId(),
                name,
                user.getEmail(),
                user.getRole(),
                avatarUrl,
                userAccessService.permissionsForRole(user.getRole())
        );
    }
}
