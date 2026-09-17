package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentAssetWriteToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["assetId","employeeId","version"],"properties":{"assetId":{"type":"integer","minimum":1},"employeeId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["assetId","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","const":"IN_USE"},"version":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    ToolDefinition assetClaimToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ASSET_CLAIM, "Assign one asset to an employee",
                "Assigns one idle tenant asset to one active employee selected by the authenticated operator.",
                "Use only for one version-bound asset assignment after explicit confirmation.",
                objectMapper.readTree(INPUT_SCHEMA), objectMapper.readTree(OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("asset:claim"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 10000);
    }
}
