package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentMeetingWriteToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["roomId","title","startAt","endAt","attendeeCount"],"properties":{"roomId":{"type":"integer","minimum":1},"title":{"type":"string","minLength":1,"maxLength":120},"agenda":{"type":"string","maxLength":500},"startAt":{"type":"string","format":"date-time"},"endAt":{"type":"string","format":"date-time"},"attendeeCount":{"type":"integer","minimum":1,"maximum":10000}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["bookingId","roomId","status","version","startAt","endAt"],"properties":{"bookingId":{"type":"integer","minimum":1},"roomId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["BOOKED","CANCELLED"]},"version":{"type":"integer","minimum":0},"startAt":{"type":"string","format":"date-time"},"endAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    public ToolDefinition meetingBookToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinition.create(
                ToolCode.MEETING_BOOK, "Book a meeting room",
                "Creates exactly one meeting-room booking owned by the authenticated user.",
                "Book one available room for one bounded future time range after explicit confirmation.",
                "1.0.0", objectMapper.readTree(INPUT_SCHEMA), objectMapper.readTree(OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("meeting:book"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, SideEffect.SINGLE_WRITE, ConfirmationPolicy.EXPLICIT,
                1, 16384, 15000, "FULL_WRITE_AUDIT");
    }
}
