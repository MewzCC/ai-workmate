package com.aiworkmate.dto;

public record ChatStreamEvent(
        String type,
        String data,
        String errorCode,
        String requestId,
        String traceId
) {
    public static ChatStreamEvent delta(String data, String requestId, String traceId) {
        return new ChatStreamEvent("delta", data, null, requestId, traceId);
    }

    public static ChatStreamEvent error(String message, String errorCode, String requestId, String traceId) {
        return new ChatStreamEvent("error", message, errorCode, requestId, traceId);
    }

    public static ChatStreamEvent done(String requestId, String traceId) {
        return new ChatStreamEvent("done", null, null, requestId, traceId);
    }
}
