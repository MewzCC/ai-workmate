package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentGenericApprovalTaskRejectToolDefinition {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["taskId","version","comment"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","applicationStatus","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["REJECTED"]},"version":{"type":"integer","minimum":1,"maximum":2147483647},"applicationStatus":{"type":"string","enum":["REJECTED"]},"action":{"type":"string","enum":["REJECT"]}}}
            """.strip();

    @Bean public ToolDefinition genericApprovalTaskRejectToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_GENERIC_TASK_REJECT, "Reject one assigned generic task",
                "Rejects exactly one currently assigned generic approval node at its expected task version with a required reason.",
                "Reject one generic task after secondary confirmation. Require taskId, task version and a nonblank rejection reason supplied by the user; ask for a missing reason, never invent one. The domain rechecks live permission, tenant, assignment, state and frozen flow order. Rejection ends this application workflow without creating a next node. Never batch or retry an unknown result.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2,
                Set.of("approval:act"), OwnershipPolicy.ASSIGNED_TO_SELF, 1, 8192, 15000);
    }
}
