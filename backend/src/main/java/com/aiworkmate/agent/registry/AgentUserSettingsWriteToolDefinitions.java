package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentUserSettingsWriteToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["model","maxContextRounds","stream","forcePdfOcr"],"properties":{"model":{"type":"string","enum":["deepseek-v4-flash","deepseek-v4-pro"]},"maxContextRounds":{"type":"integer","minimum":1,"maximum":20},"stream":{"type":"boolean"},"forcePdfOcr":{"type":"boolean"}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["model","maxContextRounds","stream","forcePdfOcr"],"properties":{"model":{"type":"string","enum":["deepseek-v4-flash","deepseek-v4-pro"]},"maxContextRounds":{"type":"integer","minimum":1,"maximum":20},"stream":{"type":"boolean"},"forcePdfOcr":{"type":"boolean"}}}
            """.strip();

    @Bean
    public ToolDefinition userSettingsUpdateToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.USER_SETTINGS_UPDATE, "Update my personal AI settings",
                "Updates the authenticated user's model, context, streaming and OCR preferences.",
                "Only update the current user's bounded preferences after explicit confirmation; never accept identity, credentials or gateway configuration.",
                objectMapper.readTree(INPUT_SCHEMA), objectMapper.readTree(OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("settings:self:update"),
                OwnershipPolicy.SELF, 1, 4096, 10000);
    }
}
