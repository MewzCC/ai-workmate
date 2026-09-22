package com.aiworkmate.security;

import jakarta.servlet.http.HttpServletRequest;

public record ClientRequestMetadata(String clientIp, String userAgent) {
    private static final int IP_LIMIT = 64;
    private static final int USER_AGENT_LIMIT = 300;

    public static ClientRequestMetadata from(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
        return new ClientRequestMetadata(sanitize(ip, IP_LIMIT),
                sanitize(request.getHeader("User-Agent"), USER_AGENT_LIMIT));
    }

    private static String sanitize(String value, int limit) {
        if (value == null) return null;
        String safe = value.replaceAll("[\\r\\n\\t]", " ").trim();
        return safe.substring(0, Math.min(safe.length(), limit));
    }
}
