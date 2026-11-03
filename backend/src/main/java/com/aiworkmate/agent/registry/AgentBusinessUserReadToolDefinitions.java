package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentBusinessUserReadToolDefinitions {
    public static final String INPUT = """
            {"type":"object","additionalProperties":false,"required":[],"properties":{"keyword":{"type":"string","maxLength":120},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();
    public static final String OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["id","name"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();
    @Bean ToolDefinition hrChangeReviewerQuery(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.HR_CHANGE_REVIEWER_QUERY, "Query employee change reviewers",
                "Lists active tenant reviewers with live hr:manage permission, excluding the applicant.",
                "Find an eligible reviewer by name before applying. Read minimal identities only; never infer permission from role names.",
                mapper.readTree(INPUT), mapper.readTree(OUTPUT), Set.of("hr:manage"), OwnershipPolicy.TENANT_SCOPED, 20, 16384, 15000);
    }
    @Bean ToolDefinition visitorHostQuery(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.VISITOR_HOST_QUERY, "Query visitor hosts",
                "Lists active host candidates in the authenticated tenant using visitor creation permission.",
                "Find a visitor host by name before applying. Returns identifiers and display names only, never contact or permission data.",
                mapper.readTree(INPUT), mapper.readTree(OUTPUT), Set.of("visitor:create"), OwnershipPolicy.TENANT_SCOPED, 20, 16384, 15000);
    }
}
