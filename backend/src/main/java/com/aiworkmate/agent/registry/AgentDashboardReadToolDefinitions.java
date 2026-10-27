package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentDashboardReadToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"days":{"type":"integer","minimum":1,"maximum":30}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["generatedAt","days","metrics","trends","businessDistribution","distributionTruncated"],"properties":{"generatedAt":{"type":"string","format":"date-time"},"days":{"type":"integer","minimum":1,"maximum":30},"metrics":{"type":"array","maxItems":4,"items":{"type":"object","additionalProperties":false,"required":["code","value"],"properties":{"code":{"type":"string","enum":["PENDING_TODOS","OVERDUE_TODOS","MY_APPLICATIONS","UNREAD_MESSAGES"]},"value":{"type":"integer","minimum":0}}}},"trends":{"type":"array","maxItems":30,"items":{"type":"object","additionalProperties":false,"required":["date","submitted","completed"],"properties":{"date":{"type":"string","format":"date"},"submitted":{"type":"integer","minimum":0},"completed":{"type":"integer","minimum":0}}}},"businessDistribution":{"type":"array","maxItems":16,"items":{"type":"object","additionalProperties":false,"required":["businessType","value"],"properties":{"businessType":{"type":"string","maxLength":80},"value":{"type":"integer","minimum":0}}}},"distributionTruncated":{"type":"boolean"}}}
            """.strip();

    @Bean
    public ToolDefinition dashboardOverviewQueryToolDefinition(ObjectMapper mapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.DASHBOARD_OVERVIEW_QUERY, "Query my dashboard overview",
                "Reads authenticated personal dashboard metrics, application trends and business distribution. No tenant-wide totals or sensitive record details.",
                "Summarize my dashboard for 1 to 30 days (default 7). Distribution is limited to the first 16 categories and explicitly reports truncation.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), Set.of("dashboard:read"),
                OwnershipPolicy.SELF, 50, 32768, 15000);
    }
}
