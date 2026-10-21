package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentAssetMetadataToolDefinitions {
    public static final String CREATE_INPUT = """
            {"type":"object","additionalProperties":false,"required":["assetCode","name","category"],"properties":{"assetCode":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"category":{"type":"string","minLength":1,"maxLength":40},"specification":{"type":"string","maxLength":120},"purchaseDate":{"type":"string","format":"date","minLength":10,"maxLength":10},"originalValue":{"type":"number","minimum":0,"maximum":999999999999.99,"multipleOf":0.01},"remark":{"type":"string","maxLength":500},"departmentId":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String UPDATE_INPUT = """
            {"type":"object","additionalProperties":false,"required":["assetId","version"],"properties":{"assetId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":0,"maximum":2147483646},"assetCode":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"category":{"type":"string","minLength":1,"maxLength":40},"specification":{"type":"string","maxLength":120},"purchaseDate":{"type":"string","format":"date","minLength":10,"maxLength":10},"originalValue":{"type":"number","minimum":0,"maximum":999999999999.99,"multipleOf":0.01},"remark":{"type":"string","maxLength":500},"clearPurchaseDate":{"type":"boolean","const":true}},"minProperties":3,"not":{"required":["purchaseDate","clearPurchaseDate"]}}
            """.strip();
    public static final String WRITE_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["assetId","assetCode","name","status","version"],"properties":{"assetId":{"type":"integer","minimum":1},"assetCode":{"type":"string","minLength":1,"maxLength":64},"name":{"type":"string","minLength":1,"maxLength":120},"status":{"type":"string","enum":["IDLE","IN_USE","REPAIRING","SCRAPPED"]},"version":{"type":"integer","minimum":0}}}
            """.strip();

    @Bean
    public ToolDefinition assetCreateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.ASSET_CREATE, "Create one asset",
                "Creates one idle unassigned tenant asset with atomic audit.",
                "Create one asset after confirmation. Code, name and category required. Department must belong to current tenant. No assignment or status input. Never retry an uncertain write; query the asset first.",
                mapper.readTree(CREATE_INPUT), mapper.readTree(WRITE_OUTPUT), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("asset:write"), OwnershipPolicy.TENANT_SCOPED, 1, 4096, 10000);
    }

    @Bean
    public ToolDefinition assetUpdateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.ASSET_UPDATE, "Update one asset",
                "Patches one tenant asset metadata at its expected version with atomic audit.",
                "Change only explicitly requested metadata after confirmation. Query asset ID and version first. Omitted fields stay unchanged; empty optional text clears it; clearPurchaseDate true clears date. No department, owner or status change. Never retry an uncertain write.",
                mapper.readTree(UPDATE_INPUT), mapper.readTree(WRITE_OUTPUT), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("asset:write"), OwnershipPolicy.TENANT_SCOPED, 1, 4096, 10000);
    }
}
