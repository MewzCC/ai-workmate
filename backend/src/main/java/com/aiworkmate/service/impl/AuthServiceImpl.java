package com.aiworkmate.service.impl;

import com.aiworkmate.dto.LoginRequest;
import com.aiworkmate.dto.LoginResponse;
import com.aiworkmate.dto.RegisterRequest;
import com.aiworkmate.dto.ResetPasswordRequest;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.AuthService;
import com.aiworkmate.util.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private static final String EMAIL_CODE_KEY = "captcha:email:";

    @Override
    public LoginResponse login(LoginRequest request) {
        // 1. 校验图形验证码
        verifyCaptcha(request.getCaptchaId(), request.getCaptchaCode());

        // 2. 查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (user == null || user.getStatus() == 0) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 3. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 4. 签发 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        // 1. 校验图形验证码
        verifyCaptcha(request.getCaptchaId(), request.getCaptchaCode());

        // 2. 校验邮箱验证码
        verifyEmailCode(request.getEmail(), request.getEmailCode());

        // 3. 检查用户名是否存在
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 4. 检查邮箱是否已注册
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, request.getEmail())
        );
        if (emailCount > 0) {
            throw new IllegalArgumentException("该邮箱已注册");
        }

        // 5. 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        // 6. 签发 JWT
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return new LoginResponse(token, user.getUsername(), user.getRole());
    }

    @Override
    public void resetPassword(ResetPasswordRequest request) {
        // 1. 校验邮箱验证码
        verifyEmailCode(request.getEmail(), request.getEmailCode());

        // 2. 查找该邮箱对应的用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, request.getEmail())
        );
        if (user == null) {
            throw new IllegalArgumentException("该邮箱未注册");
        }

        // 3. 更新密码
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .eq(User::getId, user.getId())
                        .set(User::getPassword, passwordEncoder.encode(request.getNewPassword()))
                        .set(User::getUpdatedAt, LocalDateTime.now())
        );

        log.info("Password reset for user: {}", user.getUsername());
    }

    // ===== 私有方法 =====

    private void verifyCaptcha(String captchaId, String captchaCode) {
        String key = "captcha:img:" + captchaId;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new IllegalArgumentException("图形验证码已过期，请刷新");
        }
        if (!stored.equalsIgnoreCase(captchaCode)) {
            throw new IllegalArgumentException("图形验证码错误");
        }
        redisTemplate.delete(key);
    }

    private void verifyEmailCode(String email, String emailCode) {
        String key = EMAIL_CODE_KEY + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new IllegalArgumentException("邮箱验证码已过期，请重新获取");
        }
        if (!stored.equals(emailCode)) {
            throw new IllegalArgumentException("邮箱验证码错误");
        }
        redisTemplate.delete(key);
    }
}
