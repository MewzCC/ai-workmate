package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentKnowledgeWriteToolDefinitions {
    static final String CREATE_TEXT_INPUT = """
            {"type":"object","additionalProperties":false,"required":["kbId","filename","content"],"properties":{"kbId":{"type":"integer","minimum":1},"filename":{"type":"string","minLength":1,"maxLength":255},"content":{"type":"string","minLength":1,"maxLength":12000}}}
            """.strip();
    static final String CREATE_TEXT_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["documentId","kbId","filename","status","chunkCount","createdAt"],"properties":{"documentId":{"type":"integer","minimum":1},"kbId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"status":{"type":"string","const":"READY"},"chunkCount":{"type":"integer","minimum":1},"createdAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition knowledgeDocumentCreateTextToolDefinition(ObjectMapper mapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.KNOWLEDGE_DOCUMENT_CREATE_TEXT,
                "Create one text knowledge document",
                "Creates one bounded text document in a knowledge base owned by the authenticated user.",
                "Create one confirmed text document without file-system, upload, delete or batch access.",
                mapper.readTree(CREATE_TEXT_INPUT), mapper.readTree(CREATE_TEXT_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("knowledge:search"), OwnershipPolicy.FIXED_RESOURCE,
                1, 8192, 30000);
    }
}
