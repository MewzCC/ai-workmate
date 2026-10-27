package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentObservabilityWriteToolDefinitions {
    public static final String CHART_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["charts"],"properties":{"charts":{"type":"array","minItems":1,"maxItems":12,"items":{"type":"object","additionalProperties":false,"required":["id","kind","title","mode","content","size","granularity"],"properties":{"id":{"type":"string","pattern":"^[a-z0-9-]{1,64}$"},"kind":{"type":"string","enum":["volume","risk","source","error"]},"title":{"type":"string","maxLength":40},"mode":{"type":"string","enum":["line","area","bar","mixed","donut"]},"content":{"type":"array","maxItems":8,"uniqueItems":true,"items":{"type":"string","minLength":1,"maxLength":64}},"size":{"type":"string","enum":["normal","wide"]},"granularity":{"type":"string","enum":["auto","hour","day"]}}}}}}
            """.strip();
    public static final String CHART_OUTPUT_SCHEMA = CHART_INPUT_SCHEMA;
    public static final String THRESHOLD_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"failedCount":{"type":["integer","null"],"minimum":1,"maximum":1000000},"blockedCount":{"type":["integer","null"],"minimum":1,"maximum":1000000},"p95DurationMs":{"type":["integer","null"],"minimum":1,"maximum":600000}}}
            """.strip();
    public static final String THRESHOLD_OUTPUT_SCHEMA = THRESHOLD_INPUT_SCHEMA;

    @Bean
    public ToolDefinition observabilityPreferencesUpdateToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.OBSERVABILITY_PREFERENCES_UPDATE, "Update my observability charts",
                "Updates the authenticated user's ordered platform observability chart preferences.",
                "Only configure bounded built-in chart kinds, series, layout and granularity after explicit confirmation.",
                objectMapper.readTree(CHART_INPUT_SCHEMA), objectMapper.readTree(CHART_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("runtime-log:read"),
                OwnershipPolicy.SELF, 12, 16384, 10000);
    }

    @Bean
    public ToolDefinition observabilityThresholdsUpdateToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.OBSERVABILITY_THRESHOLDS_UPDATE, "Update my observability visual thresholds",
                "Updates the authenticated user's personal visual threshold preferences.",
                "Only configure bounded personal visual cues; never create alerts, notifications, or operational actions.",
                objectMapper.readTree(THRESHOLD_INPUT_SCHEMA), objectMapper.readTree(THRESHOLD_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("runtime-log:read"),
                OwnershipPolicy.SELF, 1, 4096, 10000);
    }
}
