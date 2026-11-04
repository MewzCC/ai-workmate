package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentAtomicApplicationToolDefinitions {
    @Bean
    ToolDefinition approvalApplicationApply(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_APPLICATION_APPLY, "Apply for a generic approval",
                "Atomically creates and submits one self-owned generic form application after explicit confirmation.",
                "One write command: validate fields, freeze current definitions and create the initial approval task. No draft remains on failure.",
                mapper.readTree(AgentApprovalApplicationWriteToolDefinitions.CREATE_DRAFT_INPUT_SCHEMA),
                mapper.readTree(AgentApprovalApplicationWriteToolDefinitions.SUBMIT_DRAFT_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("approval:create", "approval:submit"), OwnershipPolicy.SELF, 1, 4096, 15000);
    }

    @Bean
    ToolDefinition expenseApply(ObjectMapper mapper) throws JsonProcessingException {
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(AgentExpenseWriteToolDefinitions.CREATE_DRAFT_INPUT_SCHEMA);
        input.putArray("required").add("amount").add("category").add("expenseDate").add("reason");
        return ToolDefinitionFactory.singleWrite(ToolCode.EXPENSE_APPLY, "Apply for an expense reimbursement",
                "Atomically creates and submits one self-owned expense application after explicit confirmation.",
                "One write command: validate amount and expense fields, freeze definitions and create the first approval task. No draft remains on failure.",
                input, mapper.readTree(AgentExpenseWriteToolDefinitions.SUBMIT_DRAFT_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("approval:create", "approval:submit"), OwnershipPolicy.SELF, 1, 4096, 15000);
    }
}
