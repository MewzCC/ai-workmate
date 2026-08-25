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

    public static final String LEAVE_MINE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN"]},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}},"oneOf":[{"required":["applicationId"],"not":{"anyOf":[{"required":["status"]},{"required":["page"]},{"required":["size"]}]}},{"not":{"required":["applicationId"]}}]}
            """.strip();

    public static final String LEAVE_MINE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","leaveType","startDate","startPeriod","endDate","endPeriod","durationHalfDays","durationDays","reason","status","version","createdAt","updatedAt"],"properties":{"id":{"type":"integer","minimum":1},"approverName":{"type":"string","maxLength":120},"leaveType":{"type":"string","maxLength":40},"startDate":{"type":"string","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"durationHalfDays":{"type":"integer","minimum":1},"durationDays":{"type":"number","minimum":0.5},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"createdAt":{"type":"string","maxLength":32},"updatedAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
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

    @Bean
    ToolDefinition leaveMineToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinition.create(
                "leave.mine",
                "Query my leave applications",
                "Returns leave applications owned by the authenticated user in the authenticated tenant.",
                "Display a bounded list or one owned leave application without exposing internal identities.",
                "1.0.0",
                objectMapper.readTree(LEAVE_MINE_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_MINE_OUTPUT_SCHEMA),
                RiskLevel.L0,
                Set.of("leave:read:self"),
                PermissionMode.ALL,
                OwnershipPolicy.SELF,
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
