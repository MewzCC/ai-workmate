package com.aiworkmate.integration;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** Shared opt-in, server-only model configuration for isolated HTTP and browser acceptance. */
final class LiveModelTestConfiguration {
    private LiveModelTestConfiguration() { }

    static void register(DynamicPropertyRegistry properties, AgentIntegrationEnvironment environment, String cookie) {
        if (!Boolean.getBoolean("agent.test.live-model")) {
            throw new IllegalStateException("Explicit -Dagent.test.live-model=true is required; no model call was authorized");
        }
        String key = setting("AI_API_KEY"), base = setting("AI_BASE_URL"), model = setting("AI_MODEL");
        try {
            URI uri = URI.create(base);
            if (!List.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("AI_BASE_URL must be a server-configured HTTP(S) endpoint without credentials or query");
        }
        if ("development-only-unconfigured".equals(key)) throw new IllegalStateException("A real server model credential is required");
        environment.register(properties);
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        properties.add("logging.level.com.aiworkmate", () -> "OFF");
        properties.add("logging.level.com.aiworkmate.agent", () -> "OFF");
        properties.add("logging.level.org.springframework.ai", () -> "OFF");
        properties.add("server.address", () -> "127.0.0.1");
        properties.add("jwt.secret", () -> secret);
        properties.add("app.auth.cookie-name", () -> cookie);
        properties.add("app.auth.cookie-secure", () -> "false");
        properties.add("spring.ai.openai.api-key", () -> key);
        properties.add("spring.ai.openai.base-url", () -> base);
        properties.add("spring.ai.openai.chat.options.model", () -> model);
        properties.add("spring.ai.retry.max-attempts", () -> "1");
        properties.add("agent.enabled", () -> "true");
        properties.add("agent.planning-enabled", () -> "true");
        properties.add("agent.execution-enabled", () -> "true");
        properties.add("agent.worker.poll-delay-ms", () -> "3600000");
        properties.add("agent.worker.heartbeat-delay-ms", () -> "3600000");
    }

    private static String setting(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Server model setting is required: " + name);
        return value;
    }
}
