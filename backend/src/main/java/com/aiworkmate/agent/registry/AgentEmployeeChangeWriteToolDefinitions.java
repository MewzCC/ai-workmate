package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentEmployeeChangeWriteToolDefinitions {
    public static final String APPLY_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["employeeUserId","changeType","effectiveDate","reviewApproverUserId","reason"],"properties":{"employeeUserId":{"type":"integer","minimum":1},"changeType":{"type":"string","enum":["ONBOARDING","REGULARIZATION","TRANSFER","OFFBOARDING"]},"effectiveDate":{"type":"string","format":"date"},"targetDepartmentId":{"type":"integer","minimum":1},"targetPositionId":{"type":"integer","minimum":1},"targetSupervisorUserId":{"type":"integer","minimum":1},"reviewApproverUserId":{"type":"integer","minimum":1},"reason":{"type":"string","minLength":1,"maxLength":1000}}}
            """.strip();
    public static final String APPLY_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["changeId","status","version","submittedAt"],"properties":{"changeId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","const":0},"submittedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String APPROVE_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["changeId","expectedVersion"],"properties":{"changeId":{"type":"integer","minimum":1},"expectedVersion":{"type":"integer","minimum":0},"comment":{"type":"string","maxLength":1000}}}
            """.strip();
    public static final String REJECT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["changeId","expectedVersion","comment"],"properties":{"changeId":{"type":"integer","minimum":1},"expectedVersion":{"type":"integer","minimum":0},"comment":{"type":"string","minLength":1,"maxLength":1000}}}
            """.strip();
    public static final String WITHDRAW_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["changeId","expectedVersion"],"properties":{"changeId":{"type":"integer","minimum":1},"expectedVersion":{"type":"integer","minimum":0}}}
            """.strip();
    public static final String ACTION_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["changeId","status","version"],"properties":{"changeId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["APPROVED","EFFECTIVE","REJECTED","WITHDRAWN"]},"version":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    ToolDefinition employeeChangeApplyToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.HR_CHANGE_APPLY, "Submit one employee change application",
                "Creates one approval-bound employee change application in the authenticated tenant.",
                "Submit one bounded employee change application only after explicit confirmation.",
                objectMapper.readTree(APPLY_INPUT_SCHEMA), objectMapper.readTree(APPLY_OUTPUT_SCHEMA),
                ToolWriteProfile.IDEMPOTENT_L1, Set.of("hr:manage"), OwnershipPolicy.TENANT_SCOPED,
                1, 8192, 15000);
    }

    @Bean
    ToolDefinition employeeChangeApproveToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return decision(objectMapper, ToolCode.HR_CHANGE_APPROVE, "Approve one employee change",
                "Approves one pending employee change assigned to the authenticated reviewer.",
                "Only approve one version-matched assigned change after secondary confirmation.",
                APPROVE_INPUT_SCHEMA);
    }

    @Bean
    ToolDefinition employeeChangeRejectToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return decision(objectMapper, ToolCode.HR_CHANGE_REJECT, "Reject one employee change",
                "Rejects one pending employee change assigned to the authenticated reviewer.",
                "Only reject one version-matched assigned change with a reason after secondary confirmation.",
                REJECT_INPUT_SCHEMA);
    }

    @Bean
    ToolDefinition employeeChangeWithdrawToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.HR_CHANGE_WITHDRAW, "Withdraw my employee change application",
                "Withdraws one pending employee change application created by the authenticated user.",
                "Only withdraw one owned version-matched pending application after explicit confirmation.",
                objectMapper.readTree(WITHDRAW_INPUT_SCHEMA), objectMapper.readTree(ACTION_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("hr:manage"), OwnershipPolicy.SELF,
                1, 4096, 10000);
    }

    private ToolDefinition decision(ObjectMapper objectMapper, ToolCode code, String name,
                                    String description, String purpose, String inputSchema)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(code, name, description, purpose,
                objectMapper.readTree(inputSchema), objectMapper.readTree(ACTION_OUTPUT_SCHEMA),
                ToolWriteProfile.SECONDARY_L2, Set.of("hr:manage"), OwnershipPolicy.ASSIGNED_TO_SELF,
                1, 4096, 10000);
    }
}
