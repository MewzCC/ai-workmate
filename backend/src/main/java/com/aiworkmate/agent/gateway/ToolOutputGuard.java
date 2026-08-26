package com.aiworkmate.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class ToolOutputGuard {
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "password", "secret", "token", "authorization", "jwt", "apikey", "api_key",
            "credential", "cookie", "sessionid", "session_id"
    );

    public boolean safe(JsonNode value) {
        if (value == null || value.isNull()) {
            return true;
        }
        if (value.isObject()) {
            var fields = value.properties();
            return fields.stream().noneMatch(entry -> forbidden(entry.getKey()) || !safe(entry.getValue()));
        }
        if (value.isArray()) {
            for (JsonNode item : value) {
                if (!safe(item)) {
                    return false;
                }
            }
        }
        if (value.isTextual()) {
            String normalized = value.asText().stripLeading().toLowerCase(java.util.Locale.ROOT);
            return !normalized.startsWith("javascript:") && !normalized.startsWith("data:text/html");
        }
        return true;
    }

    public boolean withinArrayLimit(JsonNode value, int maxItems) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return true;
        }
        if (value.isArray()) {
            if (value.size() > maxItems) {
                return false;
            }
            for (JsonNode item : value) {
                if (!withinArrayLimit(item, maxItems)) {
                    return false;
                }
            }
            return true;
        }
        return value.properties().stream().allMatch(entry -> withinArrayLimit(entry.getValue(), maxItems));
    }

    private boolean forbidden(String field) {
        return FORBIDDEN_FIELDS.contains(field.toLowerCase(java.util.Locale.ROOT));
    }
}
