package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

public record ToolDefinition(
        String code,
        String name,
        String description,
        String purpose,
        String handlerVersion,
        JsonNode inputSchema,
        JsonNode outputSchema,
        String schemaHash,
        RiskLevel riskLevel,
        Set<String> requiredPermissions,
        PermissionMode permissionMode,
        OwnershipPolicy ownershipPolicy,
        RetryPolicy retryPolicy,
        SideEffect sideEffect,
        ConfirmationPolicy confirmationPolicy,
        int maxResultItems,
        int maxResultBytes,
        int timeoutMs,
        String auditPolicy
) {
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][A-Za-z0-9]*(\\.[a-z][A-Za-z0-9]*)+$");
    private static final Set<String> PHASE_2_TOOL_CODES = Set.of(
            "todo.query", "leave.mine", "knowledge.search", "notification.mine",
            "leave.createDraft", "leave.submit"
    );
    private static final Set<String> FORBIDDEN_ARGUMENTS = Set.of(
            "userId", "tenantId", "role", "roles", "permission", "permissions", "dataScope",
            "url", "uri", "sql", "file", "filePath", "path", "script", "className", "beanName"
    );

    public static ToolDefinition create(
            String code,
            String name,
            String description,
            String purpose,
            String handlerVersion,
            JsonNode inputSchema,
            JsonNode outputSchema,
            RiskLevel riskLevel,
            Set<String> requiredPermissions,
            PermissionMode permissionMode,
            OwnershipPolicy ownershipPolicy,
            RetryPolicy retryPolicy,
            SideEffect sideEffect,
            ConfirmationPolicy confirmationPolicy,
            int maxResultItems,
            int maxResultBytes,
            int timeoutMs,
            String auditPolicy
    ) {
        String hash = sha256(handlerVersion + "\n" + inputSchema + "\n" + outputSchema);
        ToolDefinition definition = new ToolDefinition(
                code, name, description, purpose, handlerVersion, inputSchema, outputSchema, hash,
                riskLevel, Set.copyOf(requiredPermissions), permissionMode, ownershipPolicy, retryPolicy,
                sideEffect, confirmationPolicy, maxResultItems, maxResultBytes, timeoutMs, auditPolicy
        );
        definition.validate();
        return definition;
    }

    public void validate() {
        require(code != null && CODE_PATTERN.matcher(code).matches(), "Invalid tool code");
        require(PHASE_2_TOOL_CODES.contains(code), "Tool code is outside the Phase 2 capability boundary");
        require(notBlank(name) && notBlank(description) && notBlank(purpose), "Tool text metadata is required");
        require(notBlank(handlerVersion), "handlerVersion is required");
        requireClosedObjectSchema(inputSchema, "inputSchema");
        requireClosedObjectSchema(outputSchema, "outputSchema");
        validateSchemaSafety(inputSchema);
        validateSchemaSafety(outputSchema);
        require(requiredPermissions != null && !requiredPermissions.isEmpty(), "requiredPermissions is required");
        require(requiredPermissions.stream().noneMatch(p -> p == null || p.isBlank() || p.equals("*") || p.startsWith("route:")),
                "Tool permissions must be explicit business permissions");
        validatePropertyNames(inputSchema);
        require(riskLevel != null && permissionMode != null && ownershipPolicy != null, "Risk and ownership metadata is required");
        require(retryPolicy != null && sideEffect != null && confirmationPolicy != null, "Execution metadata is required");
        require(maxResultItems >= 1 && maxResultItems <= 50, "maxResultItems exceeds platform limit");
        require(maxResultBytes >= 1024 && maxResultBytes <= 262144, "maxResultBytes exceeds platform limit");
        require(timeoutMs >= 1000 && timeoutMs <= 30000, "timeoutMs exceeds platform limit");
        require(notBlank(auditPolicy), "auditPolicy is required");
        require(sideEffect != SideEffect.SINGLE_WRITE || riskLevel != RiskLevel.L0, "Write tools cannot be L0");
        require(sideEffect != SideEffect.SINGLE_WRITE || retryPolicy != RetryPolicy.READ_ONLY_SAFE,
                "Write tools cannot use read-only retry policy");
        require(riskLevel == RiskLevel.L0 || confirmationPolicy != ConfirmationPolicy.NONE,
                "Risky tools require confirmation");
        require(riskLevel != RiskLevel.L2 || confirmationPolicy == ConfirmationPolicy.SECONDARY,
                "L2 tools require secondary confirmation");
        require(riskLevel != RiskLevel.L2 || retryPolicy == RetryPolicy.NEVER,
                "L2 tools cannot be retried");
    }

    private static void requireClosedObjectSchema(JsonNode schema, String label) {
        require(schema != null && schema.isObject(), label + " must be an object");
        require("object".equals(schema.path("type").asText()), label + " must declare object type");
        require(schema.has("additionalProperties") && schema.path("additionalProperties").isBoolean()
                && !schema.path("additionalProperties").asBoolean(), label + " must close additional properties");
        require(schema.path("properties").isObject(), label + " must declare properties");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static void validatePropertyNames(JsonNode schema) {
        if (schema == null || !schema.isContainerNode()) {
            return;
        }
        JsonNode properties = schema.path("properties");
        if (properties.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                require(!FORBIDDEN_ARGUMENTS.contains(entry.getKey()), "Forbidden tool argument: " + entry.getKey());
                validatePropertyNames(entry.getValue());
            });
        }
        validatePropertyNames(schema.path("items"));
        JsonNode branches = schema.path("oneOf");
        if (branches.isArray()) {
            branches.forEach(ToolDefinition::validatePropertyNames);
        }
    }

    private static void validateSchemaSafety(JsonNode node) {
        if (node == null || !node.isContainerNode()) {
            return;
        }
        if (node.isArray()) {
            node.forEach(ToolDefinition::validateSchemaSafety);
            return;
        }
        node.properties().forEach(entry -> {
            require(!Set.of("$ref", "$dynamicRef", "$recursiveRef", "contentEncoding", "contentMediaType")
                    .contains(entry.getKey()), "External or executable schema features are forbidden");
            validateSchemaSafety(entry.getValue());
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String sha256(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
