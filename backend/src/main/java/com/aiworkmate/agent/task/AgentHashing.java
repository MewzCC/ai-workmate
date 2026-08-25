package com.aiworkmate.agent.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class AgentHashing {
    private final ObjectMapper objectMapper;

    public String hash(JsonNode value) {
        return sha256(canonicalize(value).toString());
    }

    public JsonNode canonicalize(JsonNode value) {
        if (value == null || value.isNull() || value.isValueNode()) {
            return value;
        }
        if (value.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            value.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        ObjectNode result = objectMapper.createObjectNode();
        value.properties().stream()
                .sorted(Comparator.comparing(java.util.Map.Entry::getKey))
                .forEach(entry -> result.set(entry.getKey(), canonicalize(entry.getValue())));
        return result;
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
