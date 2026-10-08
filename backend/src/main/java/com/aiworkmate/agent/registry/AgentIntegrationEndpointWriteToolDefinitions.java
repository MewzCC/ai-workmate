package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentIntegrationEndpointWriteToolDefinitions {
    static final String CREATE_DRAFT_INPUT = """
            {"type":"object","additionalProperties":false,"required":["code","name","upstreamCode","method","relativePath"],"properties":{"code":{"type":"string","pattern":"^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$","maxLength":64},"name":{"type":"string","minLength":1,"maxLength":160},"upstreamCode":{"type":"string","pattern":"^[a-z][a-z0-9-]{1,39}$","maxLength":40},"method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE"]},"relativePath":{"type":"string","minLength":1,"maxLength":500},"requestTemplate":{"type":"string","maxLength":16000},"description":{"type":"string","maxLength":2000}}}
            """.strip();
    static final String CREATE_DRAFT_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["endpointId","code","name","upstreamCode","method","relativePath","status","version","updatedAt"],"properties":{"endpointId":{"type":"integer","minimum":1},"code":{"type":"string","maxLength":64},"name":{"type":"string","maxLength":160},"upstreamCode":{"type":"string","maxLength":40},"method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE"]},"relativePath":{"type":"string","maxLength":500},"description":{"type":["string","null"],"maxLength":2000},"status":{"type":"string","const":"DRAFT"},"version":{"type":"integer","const":0},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition integrationEndpointCreateDraftToolDefinition(ObjectMapper mapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.INTEGRATION_ENDPOINT_CREATE_DRAFT,
                "Create one integration endpoint draft",
                "Creates one draft endpoint against a server-registered upstream without executing it.",
                "Create one confirmed endpoint draft using a fixed upstream code and relative path, without network execution, activation, deletion, batch or credential access.",
                mapper.readTree(CREATE_DRAFT_INPUT), mapper.readTree(CREATE_DRAFT_OUTPUT),
                ToolWriteProfile.SECONDARY_L2, Set.of("integration:endpoint:manage"),
                OwnershipPolicy.TENANT_SCOPED, 1, 8192, 10000);
    }
}
