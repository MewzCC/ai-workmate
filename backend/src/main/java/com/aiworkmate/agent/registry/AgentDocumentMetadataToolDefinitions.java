package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentDocumentMetadataToolDefinitions {
    public static final String EMPLOYEE_INPUT = input("employeeId");
    public static final String SEAL_INPUT = input("usageId");
    public static final String OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{
            "items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,
            "required":["documentId","documentType","displayName","mimeType","fileSize","createdAt"],"properties":{
            "documentId":{"type":"string","pattern":"^[1-9][0-9]{0,18}$","maxLength":19},
            "documentType":{"type":"string","enum":["CONTRACT","PROFILE","ARCHIVE"]},
            "displayName":{"type":"string","minLength":1,"maxLength":255},
            "mimeType":{"type":"string","minLength":1,"maxLength":120},
            "fileSize":{"type":"integer","minimum":1},"createdAt":{"type":"string","maxLength":32}}}},
            "total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},
            "size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();

    private static String input(String id) {
        return """
                {"type":"object","additionalProperties":false,"required":["%s"],"properties":{
                "%s":{"type":"integer","minimum":1},"page":{"type":"integer","minimum":1,"maximum":10000},
                "size":{"type":"integer","minimum":1,"maximum":20}}}
                """.formatted(id, id).strip();
    }

    @Bean
    public ToolDefinition employeeDocumentMetadata(ObjectMapper json) throws JsonProcessingException {
        return definition(json, ToolCode.HR_EMPLOYEE_DOCUMENTS_QUERY, "Query visible employee document metadata",
                "Reads bounded document metadata for one employee in the current actor's live data scope.",
                EMPLOYEE_INPUT, "hr:read");
    }

    @Bean
    public ToolDefinition sealDocumentMetadata(ObjectMapper json) throws JsonProcessingException {
        return definition(json, ToolCode.SEAL_DOCUMENTS_QUERY, "Query related seal archive metadata",
                "Reads bounded archive metadata for one related seal usage after live permission and resource checks.",
                SEAL_INPUT, "seal:register");
    }

    private ToolDefinition definition(ObjectMapper json, ToolCode code, String name, String description,
            String input, String permission) throws JsonProcessingException {
        return ToolDefinitionFactory.read(code, name, description,
                "Read business metadata only. Never returns content URLs, storage keys, uploader identities or file bytes. No file upload or download.",
                json.readTree(input), json.readTree(OUTPUT), Set.of(permission), OwnershipPolicy.FIXED_RESOURCE,
                20, 65536, 15000);
    }
}
