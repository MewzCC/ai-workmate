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
    public static final String RETURN_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["assetId","version"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String RETURN_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["assetId","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","const":"IDLE"},"version":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String REPAIR_START_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["assetId","version","reason"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String REPAIR_START_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["assetId","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"status":{"type":"string","const":"REPAIRING"},"version":{"type":"integer","minimum":1}}}
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

    @Bean
    ToolDefinition assetReturnToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ASSET_RETURN, "Return one assigned asset",
                "Returns one in-use tenant asset to the idle pool for the authenticated operator.",
                "Use only for one version-bound asset return after explicit confirmation.",
                objectMapper.readTree(RETURN_INPUT_SCHEMA), objectMapper.readTree(RETURN_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("asset:return"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 10000);
    }

    @Bean
    ToolDefinition assetRepairStartToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ASSET_REPAIR_START, "Register one asset repair",
                "Moves one idle tenant asset into repairing status with a required fault reason.",
                "Use only to register one version-bound repair after explicit confirmation.",
                objectMapper.readTree(REPAIR_START_INPUT_SCHEMA),
                objectMapper.readTree(REPAIR_START_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("asset:repair"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 10000);
    }
}
