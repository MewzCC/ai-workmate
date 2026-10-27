package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalApplicationDetailToolDefinition {
    public static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["applicationId"],"properties":{"applicationId":{"type":"integer","minimum":1},"fieldPage":{"type":"integer","minimum":1,"maximum":2}}}
            """.strip();
    public static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["application","schemaSource","fields","fieldsTotal","fieldPage"],"properties":{"application":{"type":"object","additionalProperties":false,"required":["applicationId","formKey","formName","status","version","overdue","reminderCount","canWithdraw","canEditDraft","canCancel","canRemind"],"properties":{"applicationId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":120},"formName":{"type":"string","maxLength":200},"status":{"type":"string","enum":["DRAFT","PENDING","APPROVED","REJECTED","WITHDRAWN","CANCELLED"]},"version":{"type":"integer","minimum":0},"submittedAt":{"type":"string","maxLength":32},"completedAt":{"type":"string","maxLength":32},"createdAt":{"type":"string","maxLength":32},"overdue":{"type":"boolean"},"reminderCount":{"type":"integer","minimum":0},"canWithdraw":{"type":"boolean"},"canEditDraft":{"type":"boolean"},"canCancel":{"type":"boolean"},"canRemind":{"type":"boolean"}}},"schemaSource":{"type":"string","enum":["SNAPSHOT","CURRENT_DRAFT"]},"formVersion":{"type":"integer","minimum":0},"fields":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","values","redacted"],"properties":{"name":{"type":"string","minLength":1,"maxLength":120},"label":{"type":"string","minLength":1,"maxLength":200},"type":{"type":"string","maxLength":40},"values":{"type":"array","maxItems":20,"items":{"type":"string","maxLength":500}},"redacted":{"type":"boolean"}}}},"fieldsTotal":{"type":"integer","minimum":0,"maximum":100},"fieldPage":{"type":"integer","minimum":1,"maximum":2}}}
            """.strip();

    @Bean
    public ToolDefinition approvalApplicationDetailQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.APPROVAL_APPLICATION_DETAIL_QUERY, "Read my generic application detail",
                "Reads a self-owned application through its frozen form schema; sensitive and unsupported fields are redacted.",
                "Read one existing self-owned application by applicationId. Historical fields use the submission snapshot; drafts use the current schema. No other applicant, raw schema, workflow rules or attachments.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA), Set.of("approval:mine:read"),
                OwnershipPolicy.SELF, 50, 65536, 15000);
    }
}
