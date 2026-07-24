package com.aiworkmate.service;

import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.EmailCodeLoginRequest;
import com.aiworkmate.dto.PasswordLoginRequest;
import com.aiworkmate.dto.RegisterRequest;
import com.aiworkmate.dto.ResetPasswordRequest;

public interface AuthService {
    AuthUserResponse loginWithPassword(PasswordLoginRequest request, String clientIp);
    AuthUserResponse loginWithEmailCode(EmailCodeLoginRequest request);
    AuthUserResponse register(RegisterRequest request);
    void resetPassword(ResetPasswordRequest request);
    AuthUserResponse getCurrentUser(Long userId);
}
