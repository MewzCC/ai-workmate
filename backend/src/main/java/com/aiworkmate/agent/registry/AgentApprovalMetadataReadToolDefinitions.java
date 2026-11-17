package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentApprovalMetadataReadToolDefinitions {
    public static final String INPUT_0 = """
            {"type":"object","additionalProperties":false,"required":["processId"],"properties":{"processId":{"type":"integer","minimum":1}}}
            """.strip();
    public static final String OUTPUT_0 = """
            {"type":"object","additionalProperties":false,"required":["processId","processKey","processName","status","version","nodes"],"properties":{"processId":{"type":"integer","minimum":1},"processKey":{"type":"string","maxLength":64},"processName":{"type":"string","maxLength":120},"formId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["ENABLED","DISABLED"]},"version":{"type":"integer","minimum":0},"nodes":{"type":"array","minItems":3,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["nodeType","nodeName"],"properties":{"nodeType":{"type":"string","enum":["START","APPROVAL","CONDITION","CC","DELAY","END"]},"nodeName":{"type":"string","maxLength":80},"approveType":{"type":"string","enum":["DIRECT_MANAGER","ROLE","DEPARTMENT","USER","SELF","MULTI_LEVEL"]},"targetKey":{"type":"string","maxLength":80},"mode":{"type":"string","enum":["COUNTERSIGN","OR_SIGN","SEQUENTIAL"]},"timeoutEnabled":{"type":"boolean"},"timeoutHours":{"type":"integer","minimum":1,"maximum":720},"timeoutAction":{"type":"string","enum":["REMIND","TRANSFER","AUTO_APPROVE"]}}}}}}
            """.strip();

    @Bean
    ToolDefinition approvalMetadataRead0(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.APPROVAL_PROCESS_DEFINITION_QUERY, "Read an approval process definition",
                "Reads one authorized process as bounded semantic nodes and its current version.",
                "Read tenant process nodes only; never raw JSON, scripts or another tenant.", mapper.readTree(INPUT_0), mapper.readTree(OUTPUT_0),
                Set.of("approval:read"), OwnershipPolicy.FIXED_RESOURCE, 20, 65536, 15000);
    }

    public static final String INPUT_1 = """
            {"type":"object","additionalProperties":false,"required":["taskId"],"properties":{"taskId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":64},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50},"approversOnly":{"type":"boolean"}}}
            """.strip();
    public static final String OUTPUT_1 = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size","approversOnly"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","name","canApprove"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"canApprove":{"type":"boolean"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50},"approversOnly":{"type":"boolean"}}}
            """.strip();

    @Bean
    ToolDefinition approvalMetadataRead1(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.APPROVAL_TASK_PARTICIPANTS_QUERY, "Query assigned approval participants",
                "Pages collaboration candidates for your assigned pending task; optionally restricts to eligible approvers before pagination.",
                "Read candidates for an assigned pending task; select an approver for transfer or add-sign without granting permission.", mapper.readTree(INPUT_1), mapper.readTree(OUTPUT_1),
                Set.of("approval:act"), OwnershipPolicy.ASSIGNED_TO_SELF, 50, 32768, 15000);
    }

    public static final String TEMPLATE_INPUT = """
            {"type":"object","additionalProperties":false,"required":[],"properties":{"keyword":{"type":"string","maxLength":120},"formKey":{"type":"string","pattern":"^[a-z][a-z0-9_-]{0,63}$"},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();
    public static final String PRE_TABLE_TEMPLATE_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["formId","formKey","formName","version","fields"],"properties":{"formId":{"type":"integer","minimum":1},"formKey":{"type":"string","maxLength":64},"formName":{"type":"string","maxLength":120},"version":{"type":"integer","minimum":0},"fields":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["name","label","type","required","width"],"properties":{"name":{"type":"string","pattern":"^[a-z][A-Za-z0-9_-]{0,39}$"},"label":{"type":"string","minLength":1,"maxLength":40},"type":{"type":"string","enum":["text","textarea","number","money","date","dateRange","time","radio","checkbox","select","user","department","file","image","table","divider"]},"required":{"type":"boolean"},"placeholder":{"type":"string","maxLength":80},"options":{"type":"array","maxItems":10,"items":{"type":"string","minLength":1,"maxLength":80}},"width":{"type":"string","enum":["full","half"]},"min":{"type":"number","minimum":-1000000000000000,"maximum":1000000000000000}}}},"processKey":{"type":"string","maxLength":64},"processName":{"type":"string","maxLength":120},"processVersion":{"type":"integer","minimum":0}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();

    public static final String TEMPLATE_OUTPUT = ApprovalApplicationFieldSchemas.withTemplateColumns(PRE_TABLE_TEMPLATE_OUTPUT);

    @Bean
    ToolDefinition approvalApplicationTemplateQuery(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.APPROVAL_APPLICATION_TEMPLATE_QUERY, "Query available application templates",
                "Lists enabled applicant templates, with bounded semantic fields when an exact formKey is supplied.",
                "Read enabled applicant template metadata only. Supply formKey to inspect fields. Never exposes draft configuration or application values.",
                mapper.readTree(TEMPLATE_INPUT), mapper.readTree(TEMPLATE_OUTPUT), Set.of("approval:create"),
                OwnershipPolicy.TENANT_SCOPED, 20, 196608, 15000);
    }
}
