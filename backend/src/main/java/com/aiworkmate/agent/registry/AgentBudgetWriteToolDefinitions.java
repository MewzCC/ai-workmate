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
}
