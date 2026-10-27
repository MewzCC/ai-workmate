package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentKnowledgeBaseReadToolDefinitions {
    static final String INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"properties":{"kbId":{"type":"integer","minimum":1},"limit":{"type":"integer","minimum":1,"maximum":50}}}
            """.strip();
    static final String OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["items"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["knowledgeBaseId","name","icon","documentCount","chunkCount","createdAt","updatedAt"],"properties":{"knowledgeBaseId":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":["string","null"],"maxLength":500},"documentCount":{"type":"integer","minimum":0},"chunkCount":{"type":"integer","minimum":0},"createdAt":{"type":"string","format":"date-time"},"updatedAt":{"type":"string","format":"date-time"}}}}}}
            """.strip();
    static final String DOCUMENT_INPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["kbId"],"properties":{"kbId":{"type":"integer","minimum":1},"documentId":{"type":"integer","minimum":1},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();
    static final String DOCUMENT_OUTPUT_SCHEMA = """
            {"type":"object","additionalProperties":false,"required":["knowledgeBaseId","records","total","page","size"],"properties":{"knowledgeBaseId":{"type":"integer","minimum":1},"records":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["documentId","filename","fileSize","fileType","chunkCount","status","createdAt","updatedAt"],"properties":{"documentId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"fileSize":{"type":"integer","minimum":0},"fileType":{"type":"string","maxLength":20},"chunkCount":{"type":"integer","minimum":0},"status":{"type":"string","maxLength":32},"createdAt":{"type":"string","format":"date-time"},"updatedAt":{"type":"string","format":"date-time"}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":20}}}
            """.strip();

    @Bean
    ToolDefinition knowledgeBaseQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.KNOWLEDGE_BASE_QUERY,
                "Query my knowledge bases",
                "Returns a bounded list or one safe summary of knowledge bases owned by the authenticated user.",
                "Display owned knowledge base identifiers and safe metadata before a fixed-resource operation.",
                mapper.readTree(INPUT_SCHEMA), mapper.readTree(OUTPUT_SCHEMA),
                Set.of("knowledge:search"), OwnershipPolicy.SELF, 50, 65536, 10000);
    }

    @Bean
    ToolDefinition knowledgeDocumentQueryToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.read(ToolCode.KNOWLEDGE_DOCUMENT_QUERY,
                "Query my knowledge documents",
                "Returns bounded document metadata from one knowledge base owned by the authenticated user.",
                "List documents or inspect one document identifier without exposing embeddings, hashes or chunk content.",
                mapper.readTree(DOCUMENT_INPUT_SCHEMA), mapper.readTree(DOCUMENT_OUTPUT_SCHEMA),
                Set.of("knowledge:search"), OwnershipPolicy.FIXED_RESOURCE, 20, 32768, 10000);
    }
}
