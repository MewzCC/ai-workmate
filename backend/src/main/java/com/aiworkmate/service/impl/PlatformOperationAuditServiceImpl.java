package com.aiworkmate.service.impl;

import com.aiworkmate.common.TraceContext;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.entity.PlatformOperationLog;
import com.aiworkmate.mapper.PlatformOperationLogMapper;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.PlatformOperationAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformOperationAuditServiceImpl implements PlatformOperationAuditService {
    private static final int LABEL_LIMIT = 160;
    private static final int PATH_LIMIT = 300;
    private static final int USER_AGENT_LIMIT = 300;
    private static final int ERROR_LIMIT = 80;

    private final PlatformOperationLogMapper mapper;

    @Override
    public void recordRequest(AuthenticatedUser actor, String method, String path, int statusCode,
                              long durationMs, String clientIp, String userAgent, Throwable failure) {
        if (actor == null) return;
        String eventType = "/api/auth/logout".equals(path)
                ? "LOGOUT" : ("GET".equalsIgnoreCase(method) ? "HTTP_READ" : "HTTP_WRITE");
        persist(actor.tenantId(), actor.userId(), actor.username(), eventType, method, path,
                outcome(statusCode, failure), statusCode, durationMs, clientIp, userAgent,
                failure == null ? null : failure.getClass().getSimpleName());
    }

    @Override
    public void recordLoginSuccess(AuthUserResponse actor, String loginMethod, long durationMs,
                                   String clientIp, String userAgent) {
        persist(actor.tenantId(), actor.id(), actor.name(), "LOGIN", loginMethod,
                "/api/auth/login", "SUCCEEDED", 200, durationMs, clientIp, userAgent, null);
    }

    @Override
    public void recordLoginFailure(String account, String loginMethod, long durationMs,
                                   String clientIp, String userAgent, Throwable failure) {
        String normalized = account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
        int statusCode = failure instanceof BusinessException businessException
                ? businessException.getStatus().value() : 500;
        String errorCode = failure instanceof BusinessException businessException
                && businessException.getErrorCode() != null
                ? businessException.getErrorCode()
                : failure == null ? "LOGIN_REJECTED" : failure.getClass().getSimpleName();
        persist(null, null, "account:" + sha256(normalized).substring(0, 16), "LOGIN", loginMethod,
                "/api/auth/login", statusCode >= 500 ? "FAILED" : "REJECTED", statusCode,
                durationMs, clientIp, userAgent, errorCode);
    }

    private void persist(Long tenantId, Long userId, String actorLabel, String eventType,
                           String method, String path, String outcome, Integer statusCode,
                           Long durationMs, String clientIp, String userAgent, String errorCode) {
        try {
            LocalDateTime now = LocalDateTime.now();
            PlatformOperationLog record = new PlatformOperationLog();
            record.setTenantId(tenantId);
            record.setUserId(userId);
            record.setActorLabel(limit(actorLabel, LABEL_LIMIT));
            record.setEventType(eventType);
            record.setHttpMethod(limit(method == null ? null : method.toUpperCase(Locale.ROOT), 12));
            record.setRequestPath(limit(path, PATH_LIMIT));
            record.setOutcome(outcome);
            record.setStatusCode(statusCode);
            record.setDurationMs(Math.max(0L, durationMs == null ? 0L : durationMs));
            record.setClientIp(limit(clientIp, 64));
            record.setUserAgent(limit(userAgent, USER_AGENT_LIMIT));
            record.setRequestId(contextId(TraceContext.requestId()));
            record.setTraceId(contextId(TraceContext.traceId()));
            record.setErrorCode(limit(errorCode, ERROR_LIMIT));
            record.setStartedAt(now.minusNanos(record.getDurationMs() * 1_000_000));
            record.setCompletedAt(now);
            mapper.insert(record);
        } catch (RuntimeException exception) {
            log.warn("Platform operation audit write failed, eventType={}, outcome={}",
                    eventType, outcome, exception);
        }
    }

    private String outcome(int statusCode, Throwable failure) {
        if (failure != null || statusCode >= 500) return "FAILED";
        if (statusCode == 401 || statusCode == 403) return "REJECTED";
        return statusCode < 400 ? "SUCCEEDED" : "FAILED";
    }

    private String contextId(String value) {
        return value == null || value.isBlank()
                ? UUID.randomUUID().toString().replace("-", "")
                : limit(value, 64);
    }

    private String limit(String value, int max) {
        if (value == null) return null;
        String sanitized = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return sanitized.substring(0, Math.min(sanitized.length(), max));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
