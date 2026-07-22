package com.aiworkmate.service.model;

public record ChatChunk(String type, String data, Long messageId, Long conversationId) {

    public static ChatChunk metadata(Long conversationId, Long messageId) {
        return new ChatChunk("metadata", null, messageId, conversationId);
    }

    public static ChatChunk delta(String data, Long conversationId, Long messageId) {
        return new ChatChunk("delta", data, messageId, conversationId);
    }
}
