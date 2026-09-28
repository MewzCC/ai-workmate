package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentUserPermissionToolDefinitions {
    static final String INPUT = "{" +
            "\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" +
            "\"keyword\":{\"type\":\"string\",\"maxLength\":80}," +
            "\"page\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10000}," +
            "\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}}}";
    static final String OUTPUT = "{" +
            "\"type\":\"object\",\"additionalProperties\":false," +
            "\"required\":[\"primaryRole\",\"roles\",\"dataScopes\",\"permissionVersion\",\"permissions\",\"total\",\"page\",\"size\"]," +
            "\"properties\":{" +
            "\"primaryRole\":{\"type\":\"string\",\"maxLength\":80}," +
            "\"roles\":{\"type\":\"array\",\"maxItems\":20,\"items\":{\"type\":\"string\",\"maxLength\":80}}," +
            "\"dataScopes\":{\"type\":\"array\",\"maxItems\":20,\"items\":{\"type\":\"string\",\"maxLength\":80}}," +
            "\"permissionVersion\":{\"type\":\"integer\",\"minimum\":0}," +
            "\"permissions\":{\"type\":\"array\",\"maxItems\":50,\"items\":{\"type\":\"string\",\"maxLength\":120}}," +
            "\"total\":{\"type\":\"integer\",\"minimum\":0}," +
            "\"page\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10000}," +
            "\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}}}";

    @Bean
    ToolDefinition userPermissionMineQueryToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.read(
                ToolCode.USER_PERMISSION_MINE_QUERY,
                "Query my live permissions",
                "Returns only the authenticated user's current roles, data scopes and bounded permission codes.",
                "Explain the current actor's available capabilities without accepting or exposing user or tenant identities.",
                objectMapper.readTree(INPUT),
                objectMapper.readTree(OUTPUT),
                Set.of("user-permission:read:self"),
                OwnershipPolicy.SELF,
                50,
                65536,
                15000
        );
    }
}
