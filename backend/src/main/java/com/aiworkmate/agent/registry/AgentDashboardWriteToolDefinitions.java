package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentDashboardWriteToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["metricCodes"],"properties":{"metricCodes":{"type":"array","minItems":1,"maxItems":4,"uniqueItems":true,"items":{"type":"string","enum":["PENDING_TODOS","OVERDUE_TODOS","MY_APPLICATIONS","UNREAD_MESSAGES"]}}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["metricCodes","availableMetricCodes"],"properties":{"metricCodes":{"type":"array","maxItems":4,"items":{"type":"string"}},"availableMetricCodes":{"type":"array","maxItems":4,"items":{"type":"string"}}}}
            """.strip();

    @Bean
    public ToolDefinition dashboardPreferencesUpdateToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.DASHBOARD_PREFERENCES_UPDATE, "Update my dashboard metrics",
                "Updates the authenticated user's ordered dashboard metric selection.",
                "Only select and order the fixed dashboard metrics visible to the current user after explicit confirmation.",
                objectMapper.readTree(INPUT_SCHEMA), objectMapper.readTree(OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("dashboard:read"),
                OwnershipPolicy.SELF, 1, 4096, 10000);
    }
}
