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
    public static final String UPDATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","version","fields"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"processKey":{"type":"string","minLength":1,"maxLength":64},"fields":{"type":"array","maxItems":100,"items":{"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":64},"value":{"type":"string","maxLength":500},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}}},"oneOf":[{"required":["value"],"not":{"required":["values"]}},{"required":["values"],"not":{"required":["value"]}}]}}}}
            """.strip();
    public static final String UPDATE_DRAFT_OUTPUT_SCHEMA = CREATE_DRAFT_OUTPUT_SCHEMA;
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
    public static final String CANCEL_DRAFT_INPUT_SCHEMA = SUBMIT_DRAFT_INPUT_SCHEMA;
    public static final String CANCEL_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"CANCELLED"},"version":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String REMIND_INPUT_SCHEMA = SUBMIT_DRAFT_INPUT_SCHEMA;
    public static final String REMIND_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    public ToolDefinition approvalApplicationCreateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_CREATE_DRAFT, "Create my approval application draft",
                "Creates exactly one generic approval draft owned by the authenticated user.",
                "Save one schema-validated draft without starting an approval workflow.",
                objectMapper.readTree(CREATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(CREATE_DRAFT_OUTPUT_SCHEMA), ToolWriteProfile.IDEMPOTENT_L1,
                Set.of("approval:create"), OwnershipPolicy.SELF,
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
                objectMapper.readTree(SUBMIT_DRAFT_OUTPUT_SCHEMA), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:submit"), OwnershipPolicy.SELF,
                1, 4096, 15000);
    }

    @Bean
    public ToolDefinition approvalApplicationUpdateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_UPDATE_DRAFT, "Update my approval application draft",
                "Replaces the schema-validated fields on one generic approval draft owned by the authenticated user.",
                "Update one version-bound draft without submitting it or starting a workflow.",
                objectMapper.readTree(UPDATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(UPDATE_DRAFT_OUTPUT_SCHEMA), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:create"), OwnershipPolicy.SELF,
                1, 16384, 15000);
    }

    @Bean
    public ToolDefinition approvalApplicationCancelDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_CANCEL_DRAFT, "Cancel my approval application draft",
                "Cancels exactly one generic approval draft owned by the authenticated user.",
                "Cancel one version-bound draft without deleting its audit record.",
                objectMapper.readTree(CANCEL_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(CANCEL_DRAFT_OUTPUT_SCHEMA), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:cancel"), OwnershipPolicy.SELF,
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
                objectMapper.readTree(WITHDRAW_OUTPUT_SCHEMA), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:withdraw"), OwnershipPolicy.SELF,
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
                objectMapper.readTree(REOPEN_OUTPUT_SCHEMA), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:reopen"), OwnershipPolicy.SELF,
                1, 4096, 15000);
    }

    @Bean
    public ToolDefinition approvalApplicationRemindToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.APPROVAL_APPLICATION_REMIND, "Remind the approver of my application",
                "Sends one rate-limited reminder for an owned pending generic approval application.",
                "Remind the current assignee for one version-bound application; never retry automatically.",
                objectMapper.readTree(REMIND_INPUT_SCHEMA),
                objectMapper.readTree(REMIND_OUTPUT_SCHEMA), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:remind"), OwnershipPolicy.SELF,
                1, 4096, 15000);
    }
}
