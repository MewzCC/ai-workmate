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

    @Bean
    ToolDefinition employeeChangeApplyToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.HR_CHANGE_APPLY, "Submit one employee change application",
                "Creates one approval-bound employee change application in the authenticated tenant.",
                "Submit one bounded employee change application only after explicit confirmation.",
                objectMapper.readTree(APPLY_INPUT_SCHEMA), objectMapper.readTree(APPLY_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("hr:manage"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 15000);
    }
}
