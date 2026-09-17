package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentSealWriteToolDefinitions {
    public static final String APPLY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["sealType","documentTitle","usageReason","copies"],"properties":{"sealType":{"type":"string","enum":["OFFICIAL","CONTRACT","LEGAL","FINANCE","OTHER"]},"documentTitle":{"type":"string","minLength":1,"maxLength":200},"usageReason":{"type":"string","minLength":1,"maxLength":500},"copies":{"type":"integer","minimum":1,"maximum":1000}}}
            """.strip();
    public static final String APPLY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["usageId","status","version","submittedAt"],"properties":{"usageId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","USED","RETURNED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition sealApplyToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SEAL_APPLY, "Submit one seal usage application",
                "Creates one approval-bound seal usage application owned by the authenticated applicant.",
                "Submit one bounded seal usage application only after the applicant explicitly confirms it.",
                objectMapper.readTree(APPLY_INPUT_SCHEMA), objectMapper.readTree(APPLY_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("seal:create"), OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 15000);
    }
}
