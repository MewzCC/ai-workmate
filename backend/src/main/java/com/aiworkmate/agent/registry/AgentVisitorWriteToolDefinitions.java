package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentVisitorWriteToolDefinitions {
    public static final String APPLY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["visitorName","purpose","hostUserId","expectedVisitAt","partySize"],"properties":{"visitorName":{"type":"string","minLength":1,"maxLength":60},"visitorCompany":{"type":"string","maxLength":120},"visitorPhone":{"type":"string","maxLength":40},"purpose":{"type":"string","minLength":1,"maxLength":200},"hostUserId":{"type":"integer","minimum":1},"expectedVisitAt":{"type":"string","format":"date-time"},"expectedLeaveAt":{"type":"string","format":"date-time"},"plateNumber":{"type":"string","maxLength":40},"partySize":{"type":"integer","minimum":1,"maximum":1000}}}
            """.strip();
    public static final String APPLY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["bookingId","status","version","submittedAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","WITHDRAWN","CHECKED_IN","VISITED","LEFT","NO_SHOW"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition visitorApplyToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.VISITOR_APPLY, "Submit one visitor booking application",
                "Creates one approval-bound visitor booking owned by the authenticated applicant.",
                "Submit one bounded visitor booking only after the applicant explicitly confirms it.",
                objectMapper.readTree(APPLY_INPUT_SCHEMA), objectMapper.readTree(APPLY_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("visitor:create"), OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 15000);
    }
}
