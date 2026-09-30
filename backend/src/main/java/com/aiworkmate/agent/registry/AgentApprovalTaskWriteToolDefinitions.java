package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalTaskWriteToolDefinitions {
    public static final String DECISION_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["taskId","version"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String REJECT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["taskId","version","comment"],"properties":{"taskId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"comment":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String PARTICIPANT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["taskId","targetUserId","version","reason"],"properties":{"taskId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String ADD_SIGN_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["taskId","targetUserId","version","mode","reason"],"properties":{"taskId":{"type":"integer","minimum":1},"targetUserId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"mode":{"type":"string","enum":["PRE","POST"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","taskId","status","version","action"],"properties":{"applicationId":{"type":"integer","minimum":1},"taskId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"version":{"type":"integer","minimum":0},"action":{"type":"string","enum":["APPROVE","REJECT","TRANSFER","COPY","ADD_SIGN_PRE","ADD_SIGN_POST"]}}}
            """.strip();

    @Bean
    ToolDefinition approvalTaskApproveToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return decision(mapper, ToolCode.APPROVAL_TASK_APPROVE, "Approve one assigned task",
                "Approves exactly one currently assigned approval task at its expected version.");
    }

    @Bean
    ToolDefinition approvalTaskRejectToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_TASK_REJECT, "Reject one assigned task",
                "Rejects exactly one currently assigned approval task with a bounded reason.",
                "Reject one assigned task after secondary confirmation.", mapper.readTree(REJECT_INPUT_SCHEMA),
                mapper.readTree(OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2, Set.of("approval:act"),
                OwnershipPolicy.ASSIGNED_TO_SELF, 1, 8192, 10000);
    }

    @Bean
    ToolDefinition approvalTaskTransferToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return participant(mapper, ToolCode.APPROVAL_TASK_TRANSFER, "Transfer one assigned task",
                "Transfers exactly one assigned task to one eligible approver.", ToolWriteProfile.SECONDARY_L2);
    }

    @Bean
    ToolDefinition approvalTaskCopyToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return participant(mapper, ToolCode.APPROVAL_TASK_COPY, "Copy one assigned task",
                "Sends a read-only copy of one assigned task to one active tenant user.",
                ToolWriteProfile.NON_RETRYABLE_L1);
    }

    @Bean
    ToolDefinition approvalTaskAddSignToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_TASK_ADD_SIGN, "Add one approval signer",
                "Adds exactly one eligible pre-signer or post-signer to one assigned approval task.",
                "Add one signer after secondary confirmation without changing any other task.",
                mapper.readTree(ADD_SIGN_INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA),
                ToolWriteProfile.SECONDARY_L2, Set.of("approval:act"), OwnershipPolicy.ASSIGNED_TO_SELF,
                1, 8192, 10000);
    }

    private ToolDefinition decision(ObjectMapper mapper, ToolCode code, String name, String description)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(code, name, description,
                "Decide one assigned task after secondary confirmation.", mapper.readTree(DECISION_INPUT_SCHEMA),
                mapper.readTree(OUTPUT_SCHEMA), ToolWriteProfile.SECONDARY_L2, Set.of("approval:act"),
                OwnershipPolicy.ASSIGNED_TO_SELF, 1, 8192, 10000);
    }

    private ToolDefinition participant(ObjectMapper mapper, ToolCode code, String name, String description,
                                       ToolWriteProfile profile) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(code, name, description,
                "Change one bounded participant relationship for the assigned task.",
                mapper.readTree(PARTICIPANT_INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), profile,
                Set.of("approval:act"), OwnershipPolicy.ASSIGNED_TO_SELF, 1, 8192, 10000);
    }
}
