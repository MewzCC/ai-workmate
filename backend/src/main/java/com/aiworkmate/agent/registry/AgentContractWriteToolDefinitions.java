package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentContractWriteToolDefinitions {
    public static final String CREATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["code","name","contractType","counterpartyName","ownerUserId","amount","currency","startDate","endDate"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$"},"name":{"type":"string","minLength":1,"maxLength":160},"contractType":{"type":"string","enum":["PURCHASE","SALES","SERVICE","LEASE","OTHER"]},"counterpartyName":{"type":"string","minLength":1,"maxLength":160},"supplierId":{"type":"integer","minimum":1},"ownerUserId":{"type":"integer","minimum":1},"amount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"signedDate":{"type":"string","format":"date"},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"summary":{"type":"string","maxLength":2000}}}
            """.strip();
    public static final String CREATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["contractId","code","status","version","updatedAt"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String UPDATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["contractId","version","name","contractType","counterpartyName","ownerUserId","amount","currency","startDate","endDate"],"properties":{"contractId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"name":{"type":"string","minLength":1,"maxLength":160},"contractType":{"type":"string","enum":["PURCHASE","SALES","SERVICE","LEASE","OTHER"]},"counterpartyName":{"type":"string","minLength":1,"maxLength":160},"supplierId":{"type":"integer","minimum":1},"ownerUserId":{"type":"integer","minimum":1},"amount":{"type":"number","minimum":0.01,"maximum":9999999999999999.99,"multipleOf":0.01},"currency":{"type":"string","enum":["CNY","USD","EUR","HKD"]},"signedDate":{"type":"string","format":"date"},"startDate":{"type":"string","format":"date"},"endDate":{"type":"string","format":"date"},"summary":{"type":"string","maxLength":2000}}}
            """.strip();
    public static final String UPDATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["contractId","code","status","version","updatedAt"],"properties":{"contractId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition contractCreateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.CONTRACT_CREATE_DRAFT, "Create a contract draft",
                "Creates one tenant-scoped contract draft without activating, signing, paying or sending it.",
                "Create exactly one bounded contract draft after explicit confirmation; never activate or fulfill it.",
                objectMapper.readTree(CREATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(CREATE_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("contract:manage"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT,
                1, 4096, 15000);
    }

    @Bean
    ToolDefinition contractUpdateDraftToolDefinition(ObjectMapper objectMapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.CONTRACT_UPDATE_DRAFT, "Update a contract draft",
                "Updates one tenant-scoped draft contract using optimistic locking.",
                "Replace only editable fields of one draft contract; never change its code or status.",
                objectMapper.readTree(UPDATE_DRAFT_INPUT_SCHEMA),
                objectMapper.readTree(UPDATE_DRAFT_OUTPUT_SCHEMA),
                RiskLevel.L1, Set.of("contract:manage"), OwnershipPolicy.TENANT_SCOPED,
                RetryPolicy.NEVER, ConfirmationPolicy.EXPLICIT, 1, 4096, 15000);
    }
}
