package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentNotificationWriteToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["notificationId"],"properties":{"notificationId":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["notificationId","read"],"properties":{"notificationId":{"type":"integer","minimum":1},"read":{"type":"boolean","const":true}}}
            """.strip();

    @Bean
    public ToolDefinition notificationMarkReadToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinition.create(
                ToolCode.NOTIFICATION_MARK_READ, "Mark my notification as read",
                "Marks exactly one notification owned by the authenticated user as read.",
                "Mark one self-owned notification as read after explicit confirmation without exposing business IDs.",
                "1.0.0", objectMapper.readTree(INPUT_SCHEMA), objectMapper.readTree(OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("notification:read:self"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, SideEffect.SINGLE_WRITE, ConfirmationPolicy.EXPLICIT,
                1, 4096, 5000, "FULL_WRITE_AUDIT");
    }
}
