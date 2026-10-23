package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalFormFieldPatchToolDefinitions {
    static final String INPUT = """
            {"type":"object","additionalProperties":false,"minProperties":4,"required":["formId","version","fieldName"],"properties":{"formId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1,"maximum":2147483646},"fieldName":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"width":{"type":"string","enum":["full","half"]}}}
            """.strip();
    static final String OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["formId","formKey","status","version","fieldName","label","required","placeholder","width"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"status":{"type":"string","const":"DISABLED"},"version":{"type":"integer","minimum":2,"maximum":2147483647},"fieldName":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"width":{"type":"string","enum":["full","half"]}}}
            """.strip();
    @Bean
    ToolDefinition approvalFormFieldPatchToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_FORM_PATCH_DRAFT_FIELD,
                "Patch one approval form draft field",
                "Changes only label, required, placeholder or width of one existing field in one disabled tenant form using an expected version.",
                "Patch one existing disabled-form field only; preserve all other fields and constraints. Never add, delete, rename, reorder, publish or accept raw schema JSON.",
                mapper.readTree(INPUT), mapper.readTree(OUTPUT), ToolWriteProfile.NON_RETRYABLE_L1,
                Set.of("approval:manage"), OwnershipPolicy.FIXED_RESOURCE, 1, 4096, 15000);
    }
}
