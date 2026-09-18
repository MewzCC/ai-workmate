package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentAttendanceWriteToolDefinitions {
    public static final String REISSUE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["clockDate","clockType","reason"],"properties":{"clockDate":{"type":"string","format":"date"},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String REISSUE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["reissueId","status","clockDate","clockType","submittedAt"],"properties":{"reissueId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"clockDate":{"type":"string","format":"date"},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"submittedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    public ToolDefinition attendanceReissueApplyToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.ATTENDANCE_REISSUE_APPLY, "Apply for my attendance correction",
                "Creates exactly one attendance correction request for the authenticated user.",
                "Submit one personal correction request for approval without modifying attendance records directly.",
                objectMapper.readTree(REISSUE_INPUT_SCHEMA),
                objectMapper.readTree(REISSUE_OUTPUT_SCHEMA), RiskLevel.L1,
                Set.of("attendance:reissue:apply"), OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 10000);
    }
}
