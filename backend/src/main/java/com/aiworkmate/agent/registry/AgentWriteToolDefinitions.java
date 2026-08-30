package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentWriteToolDefinitions {

    public static final String LEAVE_CREATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["leaveType","startDate","startPeriod","endDate","endPeriod","reason"],"properties":{"leaveType":{"type":"string","enum":["ANNUAL","PERSONAL","SICK","MARRIAGE","MATERNITY","PATERNITY","BEREAVEMENT","COMPENSATORY","OTHER"]},"approverUserId":{"type":"integer","minimum":1},"startDate":{"type":"string","format":"date","maxLength":10},"startPeriod":{"type":"string","enum":["AM","PM"]},"endDate":{"type":"string","format":"date","maxLength":10},"endPeriod":{"type":"string","enum":["AM","PM"]},"reason":{"type":"string","minLength":1,"maxLength":500}}}
            """.strip();

    public static final String LEAVE_CREATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":0}}}
            """.strip();

    public static final String LEAVE_SUBMIT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0}}}
            """.strip();

    public static final String LEAVE_SUBMIT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    public ToolDefinition leaveCreateDraftToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinition.create(
                "leave.createDraft", "Create my leave draft",
                "Creates exactly one draft owned by the authenticated user in the authenticated tenant.",
                "Create an idempotent leave draft only after explicit confirmation.",
                "1.0.0", objectMapper.readTree(LEAVE_CREATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_CREATE_DRAFT_OUTPUT_SCHEMA), RiskLevel.L1,
                Set.of("leave:create"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, SideEffect.SINGLE_WRITE, ConfirmationPolicy.EXPLICIT,
                1, 16384, 15000, "FULL_WRITE_AUDIT");
    }

    @Bean
    public ToolDefinition leaveSubmitToolDefinition(ObjectMapper objectMapper) throws JsonProcessingException {
        return ToolDefinition.create(
                "leave.submit", "Submit my existing leave draft",
                "Submits one pre-existing draft owned by the authenticated user in the authenticated tenant.",
                "Submit one version-bound pre-existing leave draft only after secondary confirmation.",
                "1.0.0", objectMapper.readTree(LEAVE_SUBMIT_INPUT_SCHEMA),
                objectMapper.readTree(LEAVE_SUBMIT_OUTPUT_SCHEMA), RiskLevel.L2,
                Set.of("leave:create"), PermissionMode.ALL, OwnershipPolicy.SELF,
                RetryPolicy.NEVER, SideEffect.SINGLE_WRITE, ConfirmationPolicy.SECONDARY,
                1, 16384, 15000, "FULL_WRITE_AUDIT");
    }
}
