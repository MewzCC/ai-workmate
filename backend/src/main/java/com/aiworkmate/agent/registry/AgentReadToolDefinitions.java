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

    public static final String KNOWLEDGE_SEARCH_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["query"],"properties":{"query":{"type":"string","minLength":1,"maxLength":1000},"topK":{"type":"integer","minimum":1,"maximum":10},"minScore":{"type":"number","minimum":0.0,"maximum":1.0}}}
            """.strip();

    public static final String KNOWLEDGE_SEARCH_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","untrustedContent","usagePolicy"],"properties":{"items":{"type":"array","maxItems":10,"items":{"type":"object","additionalProperties":false,"required":["content","score","matchType","citation"],"properties":{"content":{"type":"string","maxLength":12000},"score":{"type":"number","minimum":-1.0,"maximum":1.0},"matchType":{"type":"string","enum":["DENSE","SPARSE","HYBRID"]},"citation":{"type":"object","additionalProperties":false,"required":["documentId","chunkId","filename","chunkIndex"],"properties":{"documentId":{"type":"integer","minimum":1},"chunkId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"chunkIndex":{"type":"integer","minimum":0}}}}}},"untrustedContent":{"type":"boolean","const":true},"usagePolicy":{"type":"string","const":"DISPLAY_OR_SUMMARIZE_ONLY"}}}
            """.strip();

    public static final String NOTIFICATION_MINE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    public static final String NOTIFICATION_MINE_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","type","title","content","read","createdAt"],"properties":{"id":{"type":"integer","minimum":1},"type":{"type":"string","maxLength":40},"title":{"type":"string","maxLength":200},"content":{"type":"string","maxLength":2000},"businessType":{"type":"string","maxLength":40},"read":{"type":"boolean"},"createdAt":{"type":"string","maxLength":32}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}
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

    @Bean
    ToolDefinition knowledgeSearchToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinition.create(
                "knowledge.search", "Search authorized knowledge",
                "Searches only ready knowledge chunks owned by the authenticated user and tenant.",
                "Return cited untrusted knowledge data for display or a non-recursive summary.",
                "1.0.0", objectMapper.readTree(KNOWLEDGE_SEARCH_INPUT_SCHEMA),
                objectMapper.readTree(KNOWLEDGE_SEARCH_OUTPUT_SCHEMA), RiskLevel.L0,
                Set.of("knowledge:search"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE,
                10, 131072, 15000, "HASHED_ARGS_RESULT");
    }

    @Bean
    ToolDefinition notificationMineToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinition.create(
                "notification.mine", "Query my notifications",
                "Returns notifications owned by the authenticated user in the authenticated tenant.",
                "Display a bounded read-only notification list without internal business identifiers.",
                "1.0.0", objectMapper.readTree(NOTIFICATION_MINE_INPUT_SCHEMA),
                objectMapper.readTree(NOTIFICATION_MINE_OUTPUT_SCHEMA), RiskLevel.L0,
                Set.of("notification:read:self"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE,
                50, 65536, 15000, "HASHED_ARGS_RESULT");
    }
}
