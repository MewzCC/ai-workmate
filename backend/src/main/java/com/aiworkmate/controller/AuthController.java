package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.EmailCodeLoginRequest;
import com.aiworkmate.dto.PasswordLoginRequest;
import com.aiworkmate.dto.RegisterRequest;
import com.aiworkmate.dto.ResetPasswordRequest;
import com.aiworkmate.security.AuthCookieManager;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.security.ClientRequestMetadata;
import com.aiworkmate.service.AuthService;
import com.aiworkmate.service.PlatformOperationAuditService;
import com.aiworkmate.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final PlatformOperationAuditService operationAuditService;
    private final JwtUtil jwtUtil;
    private final AuthCookieManager cookieManager;

    @PostMapping("/login/password")
    public Result<AuthUserResponse> passwordLogin(@Valid @RequestBody PasswordLoginRequest request,
                                                   HttpServletRequest servletRequest,
                                                   HttpServletResponse response) {
        ClientRequestMetadata metadata = ClientRequestMetadata.from(servletRequest);
        return completeLogin(request.email(), "PASSWORD", request.remember(), metadata, response,
                () -> authService.loginWithPassword(request, metadata.clientIp()));
    }

    @PostMapping("/login/email-code")
    public Result<AuthUserResponse> emailCodeLogin(@Valid @RequestBody EmailCodeLoginRequest request,
                                                    HttpServletRequest servletRequest,
                                                    HttpServletResponse response) {
        ClientRequestMetadata metadata = ClientRequestMetadata.from(servletRequest);
        return completeLogin(request.email(), "EMAIL_CODE", request.remember(), metadata, response,
                () -> authService.loginWithEmailCode(request));
    }

    @PostMapping("/register")
    public Result<AuthUserResponse> register(@Valid @RequestBody RegisterRequest request,
                                              HttpServletResponse response) {
        AuthUserResponse user = authService.register(request);
        establishSession(user, true, response);
        return Result.ok(user);
    }

    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.ok();
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        cookieManager.clear(response);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<AuthUserResponse> currentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Result.ok(authService.getCurrentUser(principal.userId()));
    }

    private void establishSession(AuthUserResponse user, boolean remember, HttpServletResponse response) {
        cookieManager.write(response, jwtUtil.generateToken(user.id(), user.email(), user.role()), remember);
    }

    private Result<AuthUserResponse> completeLogin(String account, String loginMethod, boolean remember,
                                                   ClientRequestMetadata metadata, HttpServletResponse response,
                                                   Supplier<AuthUserResponse> authentication) {
        long started = System.nanoTime();
        try {
            AuthUserResponse user = authentication.get();
            establishSession(user, remember, response);
            operationAuditService.recordLoginSuccess(user, loginMethod, elapsedMillis(started),
                    metadata.clientIp(), metadata.userAgent());
            return Result.ok(user);
        } catch (RuntimeException exception) {
            operationAuditService.recordLoginFailure(account, loginMethod, elapsedMillis(started),
                    metadata.clientIp(), metadata.userAgent(), exception);
            throw exception;
        }
    }

    private long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }
}
