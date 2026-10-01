package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalConfigurationWriteToolDefinitions {
    static final String CREATE_FORM_DRAFT_INPUT = """
            {"type":"object","additionalProperties":false,"required":["formKey","formName","fields"],"properties":{"formKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"formName":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"fields":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","required","width"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","dateRange","time","radio","checkbox","select","user","department","file","image","table","divider"]},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},"width":{"type":"string","enum":["full","half"]}}}}}}
            """.strip();
    static final String CREATE_FORM_DRAFT_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["formId","formKey","status","version","updatedAt"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DISABLED"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    static final String UPDATE_FORM_DRAFT_INPUT = """
            {"type":"object","additionalProperties":false,"required":["formId","version","formName","fields"],"properties":{"formId":{"type":"integer","minimum":1},"version":{"type":"integer","minimum":1},"formName":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"fields":{"type":"array","minItems":1,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","required","width"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","dateRange","time","radio","checkbox","select","user","department","file","image","table","divider"]},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},"width":{"type":"string","enum":["full","half"]}}}}}}
            """.strip();
    static final String CREATE_PROCESS_DRAFT_INPUT = """
            {"type":"object","additionalProperties":false,"required":["processKey","processName","nodes"],"properties":{"processKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"processName":{"type":"string","minLength":1,"maxLength":120},"description":{"type":"string","maxLength":500},"formId":{"type":"integer","minimum":1},"nodes":{"type":"array","minItems":3,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["nodeType","nodeName"],"properties":{"nodeType":{"type":"string","enum":["START","APPROVAL","CONDITION","CC","DELAY","END"]},"nodeName":{"type":"string","minLength":1,"maxLength":80},"approveType":{"type":"string","enum":["DIRECT_MANAGER","ROLE","DEPARTMENT","USER","SELF","MULTI_LEVEL"]},"targetKey":{"type":"string","maxLength":80},"mode":{"type":"string","enum":["COUNTERSIGN","OR_SIGN","SEQUENTIAL"]},"timeoutEnabled":{"type":"boolean"},"timeoutHours":{"type":"integer","minimum":1,"maximum":720},"timeoutAction":{"type":"string","enum":["REMIND","TRANSFER","AUTO_APPROVE"]}}}}}}
            """.strip();
    static final String CREATE_PROCESS_DRAFT_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["processId","processKey","status","version","updatedAt"],"properties":{"processId":{"type":"integer","minimum":1},"processKey":{"type":"string","maxLength":64},"status":{"type":"string","const":"DISABLED"},"version":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition approvalFormCreateDraftToolDefinition(ObjectMapper mapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_FORM_CREATE_DRAFT,
                "Create an approval form draft",
                "Creates one disabled tenant approval form definition from bounded semantic fields.",
                "Create one disabled draft only; never publish, delete, upload, execute scripts or accept raw schema JSON.",
                mapper.readTree(CREATE_FORM_DRAFT_INPUT), mapper.readTree(CREATE_FORM_DRAFT_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("approval:manage"),
                OwnershipPolicy.TENANT_SCOPED, 1, 16384, 15000);
    }

    @Bean
    ToolDefinition approvalFormUpdateDraftToolDefinition(ObjectMapper mapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_FORM_UPDATE_DRAFT,
                "Update an approval form draft",
                "Updates one disabled tenant approval form definition from bounded semantic fields and an expected version.",
                "Update one disabled draft only; never change its key, publish, delete, upload, execute scripts or accept raw schema JSON.",
                mapper.readTree(UPDATE_FORM_DRAFT_INPUT), mapper.readTree(CREATE_FORM_DRAFT_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("approval:manage"),
                OwnershipPolicy.FIXED_RESOURCE, 1, 16384, 15000);
    }

    @Bean
    ToolDefinition approvalProcessCreateDraftToolDefinition(ObjectMapper mapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.APPROVAL_PROCESS_CREATE_DRAFT,
                "Create an approval process draft",
                "Creates one disabled tenant approval process from bounded semantic nodes.",
                "Create one disabled process draft only; never publish, delete, execute scripts or accept raw node JSON.",
                mapper.readTree(CREATE_PROCESS_DRAFT_INPUT), mapper.readTree(CREATE_PROCESS_DRAFT_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("approval:manage"),
                OwnershipPolicy.TENANT_SCOPED, 1, 16384, 15000);
    }
}
