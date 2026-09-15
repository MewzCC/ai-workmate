package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentTaskCenterToolDefinitions {
    static final String INPUT = "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{" +
            "\"status\":{\"type\":\"string\",\"enum\":[\"PLANNED\",\"WAITING_CONFIRMATION\",\"QUEUED\",\"RUNNING\",\"SUCCEEDED\",\"FAILED\",\"CANCELLED\",\"EXPIRED\"]}," +
            "\"from\":{\"type\":\"string\",\"format\":\"date-time\"},\"to\":{\"type\":\"string\",\"format\":\"date-time\"}," +
            "\"page\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10000},\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}}}";
    static final String OUTPUT = "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"records\",\"total\",\"page\",\"size\"],\"properties\":{" +
            "\"records\":{\"type\":\"array\",\"maxItems\":50,\"items\":{\"type\":\"object\",\"additionalProperties\":false," +
            "\"required\":[\"taskId\",\"pageId\",\"status\",\"riskLevel\",\"planVersion\",\"createdAt\",\"updatedAt\"],\"properties\":{" +
            "\"taskId\":{\"type\":\"string\",\"maxLength\":80},\"pageId\":{\"type\":\"string\",\"maxLength\":80}," +
            "\"status\":{\"type\":\"string\",\"maxLength\":40},\"riskLevel\":{\"type\":\"string\",\"maxLength\":8}," +
            "\"planVersion\":{\"type\":\"integer\",\"minimum\":1},\"createdAt\":{\"type\":\"string\",\"format\":\"date-time\"}," +
            "\"updatedAt\":{\"type\":\"string\",\"format\":\"date-time\"},\"finishedAt\":{\"type\":\"string\",\"format\":\"date-time\"}," +
            "\"errorCode\":{\"type\":\"string\",\"maxLength\":80}}}},\"total\":{\"type\":\"integer\",\"minimum\":0}," +
            "\"page\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":10000},\"size\":{\"type\":\"integer\",\"minimum\":1,\"maximum\":50}}}";

    @Bean
    ToolDefinition agentTaskMineQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinition.create(ToolCode.AGENT_TASK_MINE_QUERY, "Query my Agent tasks",
                "Returns only the authenticated user's Agent task summaries without plan, arguments or results.",
                "Display bounded personal Agent task status.", "1.0.0", mapper.readTree(INPUT), mapper.readTree(OUTPUT),
                RiskLevel.L0, Set.of("agent:task:read"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.READ_ONLY_SAFE, SideEffect.NONE, ConfirmationPolicy.NONE,
                50, 131072, 15000, "HASHED_ARGS_RESULT");
    }
}
