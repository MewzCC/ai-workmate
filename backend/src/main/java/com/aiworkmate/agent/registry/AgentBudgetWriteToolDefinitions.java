package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentBudgetWriteToolDefinitions {
    public static final String CREATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["code","name","fiscalYear","ownerUserId","totalAmount","currency","warningThreshold"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$"},"name":{"type":"string","minLength":1,"maxLength":160},"fiscalYear":{"type":"integer","minimum":2000,"maximum":2200},"ownerUserId":{"type":"integer","minimum":1},"totalAmount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"warningThreshold":{"type":"integer","minimum":1,"maximum":100},"summary":{"type":"string","maxLength":2000}}}
            """.strip();
    public static final String CREATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["budgetId","code","status","version","updatedAt"],"properties":{"budgetId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String UPDATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["budgetId","version","name","fiscalYear","ownerUserId","totalAmount","currency","warningThreshold"],"properties":{"budgetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"name":{"type":"string","minLength":1,"maxLength":160},"fiscalYear":{"type":"integer","minimum":2000,"maximum":2200},"ownerUserId":{"type":"integer","minimum":1},"totalAmount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"warningThreshold":{"type":"integer","minimum":1,"maximum":100},"summary":{"type":"string","maxLength":2000}}}
            """.strip();
    public static final String UPDATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["budgetId","code","status","version","updatedAt"],"properties":{"budgetId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String ACTIVATE_DRAFT_INPUT_SCHEMA =
            ClosedToolSchemas.versionedResourceInput("budgetId");
    public static final String ACTIVATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["budgetId","code","status","version","updatedAt"],"properties":{"budgetId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"ACTIVE"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String CANCEL_DRAFT_INPUT_SCHEMA =
            ClosedToolSchemas.versionedResourceInput("budgetId");
    public static final String CANCEL_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["budgetId","code","status","version","updatedAt"],"properties":{"budgetId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"CANCELLED"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition budgetCreateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.BUDGET_CREATE_DRAFT, "Create a budget draft",
                "Creates one tenant-scoped budget plan in draft status without activating or consuming it.",
                "Create exactly one bounded budget draft after explicit confirmation; never activate or operate it.",
                objectMapper.readTree(CREATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(CREATE_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("budget:manage"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }

    @Bean
    ToolDefinition budgetUpdateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.BUDGET_UPDATE_DRAFT, "Update a budget draft",
                "Updates one tenant-scoped draft budget using optimistic locking.",
                "Replace only the editable fields of one draft budget; never change its code, status or balances.",
                objectMapper.readTree(UPDATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(UPDATE_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("budget:manage"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }

    @Bean
    ToolDefinition budgetActivateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.BUDGET_ACTIVATE_DRAFT, "Activate a budget draft",
                "Activates one tenant-scoped draft budget using optimistic locking.",
                "Perform only the DRAFT to ACTIVE transition; never operate, close or cancel the budget.",
                objectMapper.readTree(ACTIVATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(ACTIVATE_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L2, Set.of("budget:manage"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.NEVER, ConfirmationPolicy.SECONDARY,
                1, 4096, 15000);
    }

    @Bean
    ToolDefinition budgetCancelDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.BUDGET_CANCEL_DRAFT, "Cancel a budget draft",
                "Cancels one tenant-scoped draft budget using optimistic locking.",
                "Perform only the DRAFT to CANCELLED transition; never activate, close or operate the budget.",
                objectMapper.readTree(CANCEL_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(CANCEL_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L2, Set.of("budget:manage"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.NEVER, ConfirmationPolicy.SECONDARY,
                1, 4096, 15000);
    }
}
