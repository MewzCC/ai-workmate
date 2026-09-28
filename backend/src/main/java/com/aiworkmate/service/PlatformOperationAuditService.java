package com.aiworkmate.service;

import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.security.AuthenticatedUser;

public interface PlatformOperationAuditService {
    void recordRequest(AuthenticatedUser actor, String method, String path, int statusCode,
                       long durationMs, String clientIp, String userAgent, Throwable failure);

    void recordLoginSuccess(AuthUserResponse actor, String loginMethod, long durationMs,
                            String clientIp, String userAgent);

    void recordLoginFailure(String account, String loginMethod, long durationMs,
                            String clientIp, String userAgent, Throwable failure);
}
