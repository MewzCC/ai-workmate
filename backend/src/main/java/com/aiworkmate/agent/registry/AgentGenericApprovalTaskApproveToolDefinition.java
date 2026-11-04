package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentGenericApprovalTaskApproveToolDefinition {
    public static final String INPUT_SCHEMA = AgentApprovalTaskWriteToolDefinitions.DECISION_INPUT_SCHEMA;
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","applicationStatus","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"applicationStatus":{"type":"string","enum":["PENDING","APPROVED"]},"action":{"type":"string","enum":["APPROVE"]}}}
            """.strip();

    @Bean public ToolDefinition genericApprovalTaskApproveToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_GENERIC_TASK_APPROVE, "Approve one assigned generic task",
                "Approves exactly one currently assigned generic approval node at its expected task version.",
                "Approve one generic task after secondary confirmation. Use taskId and the task version, not the application version. A current detail context may provide taskId and taskVersion, but they never authorize the write. The domain rechecks assignment, tenant, state, frozen flow order and live permission. A pending applicationStatus means handoff to the next node, not final approval. Never batch or retry an unknown result.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2,
                Set.of("approval:act"), OwnershipPolicy.ASSIGNED_TO_SELF, 1, 8192, 15000);
    }
}
