package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentAssetAssignmentToolDefinitions {
    public static final String DEPARTMENT_INPUT = AgentBusinessUserReadToolDefinitions.INPUT;
    public static final String CUSTODIAN_INPUT = """
            {"type":"object","additionalProperties":false,"required":["departmentId"],"properties":{"departmentId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":120},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();
    public static final String OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["id","name"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"departmentId":{"type":"integer","minimum":1}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();
    @Bean ToolDefinition assetDepartmentQuery(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.ASSET_DEPARTMENT_QUERY, "Find asset assignment departments",
                "Searches active departments in the authenticated tenant with asset write permission.",
                "Search departments directly using the user's stated department name or code as keyword. A keyword is a search filter, not a verified identity. No assetId or departmentId is required for this read. Return all candidates without guessing IDs. Only use this when the supplied name refers to a department. Finding the owning department of an asset identified by asset name requires asset.query; never substitute a department-name search for that request. Ambiguous write targets require clarification.",
                mapper.readTree(DEPARTMENT_INPUT), mapper.readTree(OUTPUT), Set.of("asset:write"), OwnershipPolicy.TENANT_SCOPED, 20, 16384, 15000);
    }
    @Bean ToolDefinition assetCustodianQuery(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.ASSET_CUSTODIAN_QUERY, "Find asset custodians",
                "Searches active users in one department of the authenticated tenant with asset write permission.",
                "Resolve an employee by name in a verified department before an asset operation. Returns minimal identities only; ambiguous matches require clarification.",
                mapper.readTree(CUSTODIAN_INPUT), mapper.readTree(OUTPUT), Set.of("asset:write"), OwnershipPolicy.TENANT_SCOPED, 20, 16384, 15000);
    }
}
