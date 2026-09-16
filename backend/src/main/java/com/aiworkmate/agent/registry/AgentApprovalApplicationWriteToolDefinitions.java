package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalApplicationWriteToolDefinitions {
    public static final String CREATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["formKey","fields"],"properties":{"formKey":{"type":"string","minLength":1,"maxLength":64},"processKey":{"type":"string","minLength":1,"maxLength":64},"fields":{"type":"array","maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":64},"value":{"type":"string","maxLength":500},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}}},"oneOf":[{"required":["value"],"not":{"required":["values"]}},{"required":["values"],"not":{"required":["value"]}}]}}}}
            """.strip();
    public static final String CREATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":0}}}
            """.strip();
    public static final String SUBMIT_DRAFT_INPUT_SCHEMA =
            ClosedToolSchemas.versionedResourceInput("applicationId");
    public static final String SUBMIT_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String WITHDRAW_INPUT_SCHEMA = SUBMIT_DRAFT_INPUT_SCHEMA;
    public static final String WITHDRAW_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"WITHDRAWN"},"version":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String REOPEN_INPUT_SCHEMA = SUBMIT_DRAFT_INPUT_SCHEMA;
    public static final String REOPEN_OUTPUT_SCHEMA = CREATE_DRAFT_OUTPUT_SCHEMA;

    @Bean
    public ToolDefinition approvalApplicationCreateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT, "Create my approval application draft",
                "Creates exactly one generic approval draft owned by the authenticated user.",
                "Save one schema-validated draft without starting an approval workflow.",
                objectMapper.readTree(CREATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(CREATE_DRAFT_OUTPUT_SCHEMA), RiskLevel.L1,
                Set.of("approval:create"), OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 16384, 15000);
    }

    @Bean
    public ToolDefinition approvalApplicationSubmitDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_SUBMIT_DRAFT, "Submit my approval application draft",
                "Submits exactly one generic approval draft owned by the authenticated user.",
                "Atomically freeze the configured form and workflow, then create the first approval task.",
                objectMapper.readTree(SUBMIT_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(SUBMIT_DRAFT_OUTPUT_SCHEMA), RiskLevel.L1,
                Set.of("approval:submit"), OwnershipPolicy.SELF,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }

    @Bean
    public ToolDefinition approvalApplicationWithdrawToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_WITHDRAW, "Withdraw my approval application",
                "Withdraws exactly one pending generic approval application owned by the authenticated user.",
                "Atomically cancel the current approval task and workflow instance for one owned application.",
                objectMapper.readTree(WITHDRAW_INPUT_SCHEMA),
                objectMapper.readTree(WITHDRAW_OUTPUT_SCHEMA), RiskLevel.L1,
                Set.of("approval:withdraw"), OwnershipPolicy.SELF,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }

    @Bean
    public ToolDefinition approvalApplicationReopenToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_REOPEN, "Reopen my approval application as draft",
                "Reopens exactly one rejected or withdrawn generic application owned by the authenticated user.",
                "Restore one completed application to an editable draft while preserving its prior workflow history.",
                objectMapper.readTree(REOPEN_INPUT_SCHEMA),
                objectMapper.readTree(REOPEN_OUTPUT_SCHEMA), RiskLevel.L1,
                Set.of("approval:reopen"), OwnershipPolicy.SELF,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }
}
