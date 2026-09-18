package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentLeaveWithdrawalToolDefinitions {
    public static final String INPUT_SCHEMA = ClosedToolSchemas.versionedResourceInput("applicationId");
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"WITHDRAWN"},"version":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    public ToolDefinition leaveWithdrawalToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.LEAVE_WITHDRAW, "Withdraw my leave application",
                "Withdraws exactly one pending leave application owned by the authenticated user.",
                "Withdraw one owned pending application at its expected version after explicit confirmation; never retry automatically.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("leave:withdraw"), OwnershipPolicy.SELF,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 10000);
    }
}
