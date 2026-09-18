package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentExpenseWriteToolDefinitions {
    public static final String CREATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"minProperties":1,"properties":{"amount":{"type":"number","minimum":0.01,"maximum":999999999.99,"multipleOf":0.01},"category":{"type":"string","enum":["TRAVEL","MEAL","TRANSPORT","OFFICE","OTHER"]},"expenseDate":{"type":"string","format":"date"},"invoiceNumber":{"type":"string","minLength":1,"maxLength":100},"reason":{"type":"string","minLength":1,"maxLength":1000}}}
            """.strip();
    public static final String CREATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version","createdAt"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"version":{"type":"integer","minimum":0},"createdAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String SUBMIT_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646}}}
            """.strip();
    public static final String SUBMIT_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"PENDING"},"version":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String WITHDRAW_INPUT_SCHEMA = SUBMIT_DRAFT_INPUT_SCHEMA;
    public static final String WITHDRAW_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId","formKey","status","version"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","const":"expense-application"},"status":{"type":"string","const":"WITHDRAWN"},"version":{"type":"integer","minimum":1}}}
            """.strip();

    @Bean
    ToolDefinition expenseCreateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.EXPENSE_CREATE_DRAFT, "Create my expense draft",
                "Creates one self-owned expense reimbursement draft without submitting it.",
                "Create one bounded expense draft only after explicit confirmation; never submit or approve it.",
                objectMapper.readTree(CREATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(CREATE_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("approval:create"), OwnershipPolicy.SELF,
                RetryPolicy.BUSINESS_IDEMPOTENT, ConfirmationPolicy.EXPLICIT,
                1, 8192, 15000);
    }

    @Bean
    ToolDefinition expenseSubmitDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.EXPENSE_SUBMIT_DRAFT, "Submit my expense draft",
                "Submits one self-owned expense draft and starts its approval workflow.",
                "Submit only a verified expense-application draft after explicit confirmation.",
                objectMapper.readTree(SUBMIT_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(SUBMIT_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("approval:submit"), OwnershipPolicy.SELF,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }

    @Bean
    ToolDefinition expenseWithdrawToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.EXPENSE_WITHDRAW, "Withdraw my expense application",
                "Withdraws one self-owned pending expense application.",
                "Cancel the active approval task and workflow only for a verified expense application.",
                objectMapper.readTree(WITHDRAW_INPUT_SCHEMA),
                objectMapper.readTree(WITHDRAW_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("approval:withdraw"), OwnershipPolicy.SELF,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }
}
