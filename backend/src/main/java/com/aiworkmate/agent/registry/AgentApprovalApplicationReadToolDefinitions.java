package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalApplicationReadToolDefinitions {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"formKey":{"type":"string","minLength":1,"maxLength":120},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["applicationId","formKey","formName","status","version","overdue","reminderCount","canWithdraw","canEditDraft","canCancel","canRemind"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":120},"formName":{"type":"string","maxLength":200},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"createdAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"},"reminderCount":{"type":"integer","minimum":0},"canWithdraw":{"type":"boolean"},"canEditDraft":{"type":"boolean"},"canCancel":{"type":"boolean"},"canRemind":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();

    @Bean
    public ToolDefinition approvalApplicationMineQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.APPROVAL_APPLICATION_MINE_QUERY, "Query my generic applications",
                "Lists current-user generic form applications including drafts; excludes raw form values, schemas and other identities.",
                "Read my generic applications with optional status/formKey and bounded pagination. Leave requests are a separate tool. Never reads another applicant or tenant.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), Set.of("approval:mine:read"),
                OwnershipPolicy.SELF, 50, 32768, 15000);
    }
}
