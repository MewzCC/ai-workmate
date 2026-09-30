package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentSupplierWriteToolDefinitions {
    public static final String CREATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["code","name","category","supplierLevel"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$"},"name":{"type":"string","minLength":1,"maxLength":160},"shortName":{"type":"string","maxLength":80},"category":{"type":"string","enum":["MATERIAL","SERVICE","LOGISTICS","CONSULTING","OTHER"]},"supplierLevel":{"type":"string","enum":["STRATEGIC","PREFERRED","STANDARD","RESTRICTED"]},"paymentTerms":{"type":"string","maxLength":120}}}
            """.strip();
    public static final String CREATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["supplierId","code","status","version","updatedAt"],"properties":{"supplierId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String UPDATE_DRAFT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["supplierId","version","name","category","supplierLevel"],"properties":{"supplierId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"name":{"type":"string","minLength":1,"maxLength":160},"shortName":{"type":"string","maxLength":80},"category":{"type":"string","enum":["MATERIAL","SERVICE","LOGISTICS","CONSULTING","OTHER"]},"supplierLevel":{"type":"string","enum":["STRATEGIC","PREFERRED","STANDARD","RESTRICTED"]},"paymentTerms":{"type":"string","maxLength":120}}}
            """.strip();
    public static final String UPDATE_DRAFT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["supplierId","code","status","version","updatedAt"],"properties":{"supplierId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    public static final String UPDATE_STATUS_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["supplierId","version","status"],"properties":{"supplierId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"status":{"type":"string","enum":["ACTIVE","SUSPENDED","BLACKLISTED"]},"reason":{"type":"string","maxLength":500}}}
            """.strip();
    public static final String UPDATE_STATUS_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["supplierId","code","status","version","updatedAt"],"properties":{"supplierId":{"type":"integer","minimum":1},"code":{"type":"string"},"status":{"type":"string","enum":["ACTIVE","SUSPENDED","BLACKLISTED"]},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition supplierCreateDraftToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SUPPLIER_CREATE_DRAFT, "Create a supplier draft",
                "Creates one tenant-scoped supplier draft without contact, credit or risk data.",
                "Create exactly one bounded supplier draft after explicit confirmation; never activate it.",
                mapper.readTree(CREATE_DRAFT_INPUT_SCHEMA), mapper.readTree(CREATE_DRAFT_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("supplier:manage"), OwnershipPolicy.TENANT_SCOPED, 1, 4096, 15000);
    }

    @Bean
    ToolDefinition supplierUpdateDraftToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SUPPLIER_UPDATE_DRAFT, "Update a supplier draft",
                "Updates one tenant-scoped supplier draft using optimistic locking.",
                "Update exactly one bounded supplier draft after explicit confirmation; never activate it.",
                mapper.readTree(UPDATE_DRAFT_INPUT_SCHEMA), mapper.readTree(UPDATE_DRAFT_OUTPUT_SCHEMA),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("supplier:manage"), OwnershipPolicy.TENANT_SCOPED, 1, 4096, 15000);
    }

    @Bean
    ToolDefinition supplierUpdateStatusToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(
                ToolCode.SUPPLIER_UPDATE_STATUS, "Update one supplier status",
                "Applies one allowed status transition to one tenant-scoped supplier using optimistic locking.",
                "Update exactly one supplier status after secondary confirmation; preserve status history and audit.",
                mapper.readTree(UPDATE_STATUS_INPUT_SCHEMA), mapper.readTree(UPDATE_STATUS_OUTPUT_SCHEMA),
                ToolWriteProfile.SECONDARY_L2, Set.of("supplier:manage"),
                OwnershipPolicy.TENANT_SCOPED, 1, 4096, 15000);
    }
}
