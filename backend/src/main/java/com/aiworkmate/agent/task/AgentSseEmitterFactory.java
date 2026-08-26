package com.aiworkmate.agent.task;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AgentSseEmitterFactory {
    public SseEmitter create(long timeoutMs) {
        return new SseEmitter(timeoutMs);
    }
}
