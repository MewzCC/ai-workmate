package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentObservabilityReadToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"range":{"type":"string","enum":["24h","7d","30d"]}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["from","to","interval","stats","timeline","sources","errorCodes","categoriesTruncated"],"properties":{"from":{"type":"string","format":"date-time"},"to":{"type":"string","format":"date-time"},"interval":{"type":"string","enum":["hour","day"]},"stats":{"type":"object","additionalProperties":false,"required":["total","succeeded","failed","blocked"],"properties":{"total":{"type":"integer","minimum":0},"succeeded":{"type":"integer","minimum":0},"failed":{"type":"integer","minimum":0},"blocked":{"type":"integer","minimum":0},"averageDurationMs":{"type":"integer","minimum":0}}},"p95DurationMs":{"type":"integer","minimum":0},"timeline":{"type":"array","maxItems":31,"items":{"type":"object","additionalProperties":false,"required":["bucket","total","failed","blocked"],"properties":{"bucket":{"type":"string","format":"date-time"},"total":{"type":"integer","minimum":0},"failed":{"type":"integer","minimum":0},"blocked":{"type":"integer","minimum":0}}}},"sources":{"type":"array","maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["code","total"],"properties":{"code":{"type":"string","maxLength":120},"total":{"type":"integer","minimum":0}}}},"errorCodes":{"type":"array","maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["code","total"],"properties":{"code":{"type":"string","maxLength":120},"total":{"type":"integer","minimum":0}}}},"categoriesTruncated":{"type":"boolean"}}}
            """.strip();

    @Bean
    public ToolDefinition observabilityOverviewQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.OBSERVABILITY_OVERVIEW_QUERY, "Query platform observability",
                "Reads current-tenant runtime aggregates and bounded complete timeline without identities or payloads.",
                "Observe totals, failures, blocked calls, latency and trends for 24h, 7d (default), or 30d. Categories are bounded; no cross-tenant query or export.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), Set.of("runtime-log:read"),
                OwnershipPolicy.TENANT_SCOPED, 50, 32768, 15000);
    }
}
