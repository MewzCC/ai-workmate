package com.aiworkmate.service;

import com.aiworkmate.dto.LoginRequest;
import com.aiworkmate.dto.LoginResponse;
import com.aiworkmate.dto.RegisterRequest;
import com.aiworkmate.dto.ResetPasswordRequest;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse register(RegisterRequest request);

    void resetPassword(ResetPasswordRequest request);
}
