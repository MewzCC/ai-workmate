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
    static final String CANCEL_INPUT = "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"taskId\"],\"properties\":{" +
            "\"taskId\":{\"type\":\"string\",\"minLength\":1,\"maxLength\":80}}}";
    static final String CANCEL_OUTPUT = "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"taskId\",\"status\",\"updatedAt\"],\"properties\":{" +
            "\"taskId\":{\"type\":\"string\",\"maxLength\":80},\"status\":{\"type\":\"string\",\"const\":\"CANCELLED\"}," +
            "\"updatedAt\":{\"type\":\"string\",\"format\":\"date-time\"}}}";

    @Bean
    ToolDefinition agentTaskMineQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.AGENT_TASK_MINE_QUERY, "Query my Agent tasks",
                "Returns only the authenticated user's Agent task summaries without plan, arguments or results.",
                "Display bounded personal Agent task status.", mapper.readTree(INPUT), mapper.readTree(OUTPUT),
                Set.of("agent:task:read"), OwnershipPolicy.SELF, 50, 131072, 15000);
    }

    @Bean
    ToolDefinition agentTaskCancelToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.AGENT_TASK_CANCEL, "Cancel my Agent task",
                "Cancels exactly one cancellable Agent task owned by the authenticated user.",
                "Cancel one owned Agent task after explicit confirmation without retrying the write.",
                mapper.readTree(CANCEL_INPUT), mapper.readTree(CANCEL_OUTPUT), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("agent:task:read"), OwnershipPolicy.SELF, 1, 4096, 10000);
    }
}
