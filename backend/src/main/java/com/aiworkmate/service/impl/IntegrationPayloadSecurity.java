package com.aiworkmate.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class IntegrationPayloadSecurity {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "authorization", "cookie", "password", "passwd", "secret", "token",
            "apikey", "api_key", "accesskey", "privatekey");
    private static final Pattern SENSITIVE_TEXT = Pattern.compile(
            "(?i)(authorization|cookie|password|passwd|secret|token|api[_-]?key|access[_-]?key|private[_-]?key)(\\s*[:=]\\s*)([^\\s,;]+)");

    private final ObjectMapper objectMapper;

    public String sanitizeResponsePreview(String preview) {
        if (!StringUtils.hasText(preview)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(preview);
            redactSensitiveValues(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ignored) {
            return SENSITIVE_TEXT.matcher(preview).replaceAll("$1$2[REDACTED]");
        }
    }

    public String requestHash(String method, String path, String body) {
        return sha256(method + "\n" + path + "\n" + Objects.toString(body, ""));
    }

    public String responseHash(String preview) {
        return StringUtils.hasText(preview) ? sha256(preview) : null;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void redactSensitiveValues(JsonNode node) {
        if (node.isObject()) {
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (isSensitiveKey(name)) {
                    ((ObjectNode) node).put(name, "[REDACTED]");
                } else {
                    redactSensitiveValues(node.get(name));
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::redactSensitiveValues);
        }
    }

    private boolean isSensitiveKey(String name) {
        String normalized = name.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream()
                .map(key -> key.replace("_", ""))
                .anyMatch(normalized::equals);
    }
}
