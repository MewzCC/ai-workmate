package com.aiworkmate.dto;

/** Classification is a routing hint, never an execution permit or a tool plan. */
public record AiIntentResponse(Intent intent) {
    public enum Intent { CHAT, OPERATION }
}
