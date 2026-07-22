package com.aiworkmate.dto;

public record ChatStreamEvent(
        String type,
        String data,
        String errorCode,
        Long messageId,
        Long conversationId,
        String requestId,
        String traceId
) {
    public static ChatStreamEvent delta(String data, String requestId, String traceId) {
        return new ChatStreamEvent("delta", data, null, null, null, requestId, traceId);
    }

    public static ChatStreamEvent chunk(String type, String data, Long messageId, Long conversationId,
                                        String requestId, String traceId) {
        return new ChatStreamEvent(type, data, null, messageId, conversationId, requestId, traceId);
    }

    public static ChatStreamEvent error(String message, String errorCode, String requestId, String traceId) {
        return new ChatStreamEvent("error", message, errorCode, null, null, requestId, traceId);
    }

    public static ChatStreamEvent done(String requestId, String traceId) {
        return new ChatStreamEvent("done", null, null, null, null, requestId, traceId);
    }
}
