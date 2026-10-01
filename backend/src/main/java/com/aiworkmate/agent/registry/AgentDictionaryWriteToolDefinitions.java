package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentDictionaryWriteToolDefinitions {
    static final String CREATE_TYPE_INPUT = """
            {"type":"object","additionalProperties":false,"required":["code","name"],"properties":{"code":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"sortOrder":{"type":"integer","minimum":0,"maximum":9999}}}
            """.strip();
    static final String CREATE_TYPE_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["dictionaryTypeId","code","name","status","sortOrder","version","updatedAt"],"properties":{"dictionaryTypeId":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":64},"name":{"type":"string","maxLength":120},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","const":"ACTIVE"},"sortOrder":{"type":"integer","minimum":0,"maximum":9999},"version":{"type":"integer","minimum":0},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    static final String UPDATE_TYPE_INPUT = """
            {"type":"object","additionalProperties":false,"minProperties":3,"required":["code","version"],"properties":{"code":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"version":{"type":"integer","minimum":0,"maximum":2147483646},"name":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"sortOrder":{"type":"integer","minimum":0,"maximum":9999}}}
            """.strip();
    static final String UPDATE_TYPE_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["dictionaryTypeId","code","name","status","sortOrder","version","updatedAt"],"properties":{"dictionaryTypeId":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":64},"name":{"type":"string","maxLength":120},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","enum":["ACTIVE","DISABLED"]},"sortOrder":{"type":"integer","minimum":0,"maximum":9999},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    static final String CREATE_ITEM_INPUT = """
            {"type":"object","additionalProperties":false,"required":["typeCode","value","label"],"properties":{"typeCode":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"value":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$","maxLength":128},"label":{"type":"string","minLength":1,"maxLength":160},"description":{"type":"string","maxLength":500},"sortOrder":{"type":"integer","minimum":0,"maximum":9999}}}
            """.strip();
    static final String CREATE_ITEM_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["dictionaryItemId","typeCode","value","label","status","sortOrder","usageCount","version","updatedAt"],"properties":{"dictionaryItemId":{"type":"integer","minimum":1},"typeCode":{"type":"string","maxLength":64},"value":{"type":"string","maxLength":128},"label":{"type":"string","maxLength":160},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","const":"ACTIVE"},"sortOrder":{"type":"integer","minimum":0,"maximum":9999},"usageCount":{"type":"integer","const":0},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    static final String UPDATE_ITEM_INPUT = """
            {"type":"object","additionalProperties":false,"minProperties":4,"required":["typeCode","value","version"],"properties":{"typeCode":{"type":"string","pattern":"^[A-Z][A-Z0-9_]{1,63}$","maxLength":64},"value":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$","maxLength":128},"version":{"type":"integer","minimum":0,"maximum":2147483646},"label":{"type":"string","minLength":1,"maxLength":160},"description":{"type":"string","maxLength":500},"sortOrder":{"type":"integer","minimum":0,"maximum":9999}}}
            """.strip();
    static final String UPDATE_ITEM_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["dictionaryItemId","typeCode","value","label","status","sortOrder","usageCount","version","updatedAt"],"properties":{"dictionaryItemId":{"type":"integer","minimum":1},"typeCode":{"type":"string","maxLength":64},"value":{"type":"string","maxLength":128},"label":{"type":"string","maxLength":160},"description":{"type":["string","null"],"maxLength":500},"status":{"type":"string","enum":["ACTIVE","DISABLED"]},"sortOrder":{"type":"integer","minimum":0,"maximum":9999},"usageCount":{"type":"integer","minimum":0},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition dictionaryTypeCreateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.DICTIONARY_TYPE_CREATE,
                "Create one dictionary type",
                "Creates one active tenant dictionary type with bounded non-sensitive metadata.",
                "Create one confirmed dictionary type without item creation, status changes, deletion, batch or security configuration access.",
                mapper.readTree(CREATE_TYPE_INPUT), mapper.readTree(CREATE_TYPE_OUTPUT),
                ToolWriteProfile.SECONDARY_L2, Set.of("dictionary:manage"), OwnershipPolicy.TENANT_SCOPED,
                1, 8192, 10000);
    }

    @Bean
    ToolDefinition dictionaryTypeUpdateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.DICTIONARY_TYPE_UPDATE,
                "Update one dictionary type",
                "Updates bounded metadata on one tenant dictionary type selected by its immutable code.",
                "Update one confirmed dictionary type with optimistic locking, without code or status changes, deletion, item changes, batch or security configuration access.",
                mapper.readTree(UPDATE_TYPE_INPUT), mapper.readTree(UPDATE_TYPE_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("dictionary:manage"), OwnershipPolicy.FIXED_RESOURCE,
                1, 8192, 10000);
    }

    @Bean
    ToolDefinition dictionaryItemCreateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.DICTIONARY_ITEM_CREATE,
                "Create one dictionary item",
                "Creates one active item inside one active tenant dictionary selected by immutable type code.",
                "Create one confirmed dictionary item without update, status changes, deletion, batch import or security configuration access.",
                mapper.readTree(CREATE_ITEM_INPUT), mapper.readTree(CREATE_ITEM_OUTPUT),
                ToolWriteProfile.SECONDARY_L2, Set.of("dictionary:manage"), OwnershipPolicy.FIXED_RESOURCE,
                1, 8192, 10000);
    }

    @Bean
    ToolDefinition dictionaryItemUpdateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.DICTIONARY_ITEM_UPDATE,
                "Update one dictionary item",
                "Updates bounded metadata on one tenant dictionary item selected by immutable type code and value.",
                "Update one confirmed dictionary item with optimistic locking, without value or status changes, deletion, batch import or security configuration access.",
                mapper.readTree(UPDATE_ITEM_INPUT), mapper.readTree(UPDATE_ITEM_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("dictionary:manage"), OwnershipPolicy.FIXED_RESOURCE,
                1, 8192, 10000);
    }
}
