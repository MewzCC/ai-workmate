package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentReadToolDefinitions {

    public static final String TODO_QUERY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","CANCELLED"]},"from":{"type":"string","minLength":16,"maxLength":32},"to":{"type":"string","minLength":16,"maxLength":32},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String TODO_QUERY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicationId","applicantName","leaveType","durationHalfDays","status","version","overdue"],"properties":{"id":{"type":"integer","minimum":1},"applicationId":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"durationHalfDays":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"dueAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    @Bean
    ToolDefinition todoQueryToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinition.create(
                "todo.query",
                "Query my approval tasks",
                "Returns approval tasks assigned to the authenticated user in the authenticated tenant.",
                "Display a bounded, read-only list of the current user's approval tasks.",
                "1.0.0",
                objectMapper.readTree(TODO_QUERY_INPUT_SCHEMA),
                objectMapper.readTree(TODO_QUERY_OUTPUT_SCHEMA),
                RiskLevel.L0,
                Set.of("todo:read"),
                PermissionMode.ALL,
                OwnershipPolicy.ASSIGNED_TO_SELF,
                RetryPolicy.READ_ONLY_SAFE,
                SideEffect.NONE,
                ConfirmationPolicy.NONE,
                50,
                65536,
                15000,
                "HASHED_ARGS_RESULT"
        );
    }
}
