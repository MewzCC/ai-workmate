package com.aiworkmate.agent.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration(proxyBeanMethods = false)
public class AgentKnowledgeWriteToolDefinitions {
    static final String CREATE_BASE_INPUT = """
            {"type":"object","additionalProperties":false,"required":["name"],"properties":{"name":{"type":"string","minLength":1,"maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":"string","maxLength":500}}}
            """.strip();
    static final String CREATE_BASE_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["knowledgeBaseId","name","icon","documentCount","chunkCount","createdAt"],"properties":{"knowledgeBaseId":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":["string","null"],"maxLength":500},"documentCount":{"type":"integer","minimum":0},"chunkCount":{"type":"integer","minimum":0},"createdAt":{"type":"string","format":"date-time"}}}
            """.strip();
    static final String UPDATE_BASE_INPUT = """
            {"type":"object","additionalProperties":false,"minProperties":2,"required":["kbId"],"properties":{"kbId":{"type":"integer","minimum":1},"name":{"type":"string","minLength":1,"maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":"string","maxLength":500},"chunkSize":{"type":"integer","minimum":100,"maximum":8000},"chunkOverlap":{"type":"integer","minimum":0,"maximum":4000},"denseTopK":{"type":"integer","minimum":1,"maximum":50},"sparseTopK":{"type":"integer","minimum":0,"maximum":50}}}
            """.strip();
    static final String UPDATE_BASE_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["knowledgeBaseId","name","icon","documentCount","chunkCount","chunkSize","chunkOverlap","denseTopK","sparseTopK","updatedAt"],"properties":{"knowledgeBaseId":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":80},"icon":{"type":"string","maxLength":40},"description":{"type":["string","null"],"maxLength":500},"documentCount":{"type":"integer","minimum":0},"chunkCount":{"type":"integer","minimum":0},"chunkSize":{"type":"integer","minimum":100,"maximum":8000},"chunkOverlap":{"type":"integer","minimum":0,"maximum":4000},"denseTopK":{"type":"integer","minimum":1,"maximum":50},"sparseTopK":{"type":"integer","minimum":0,"maximum":50},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();
    static final String CREATE_TEXT_INPUT = """
            {"type":"object","additionalProperties":false,"required":["kbId","filename","content"],"properties":{"kbId":{"type":"integer","minimum":1},"filename":{"type":"string","minLength":1,"maxLength":255},"content":{"type":"string","minLength":1,"maxLength":12000}}}
            """.strip();
    static final String CREATE_TEXT_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["documentId","kbId","filename","status","chunkCount","createdAt"],"properties":{"documentId":{"type":"integer","minimum":1},"kbId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"status":{"type":"string","const":"READY"},"chunkCount":{"type":"integer","minimum":1},"createdAt":{"type":"string","format":"date-time"}}}
            """.strip();
    static final String REINDEX_DOCUMENT_INPUT = """
            {"type":"object","additionalProperties":false,"required":["documentId"],"properties":{"documentId":{"type":"integer","minimum":1}}}
            """.strip();
    static final String REINDEX_DOCUMENT_OUTPUT = """
            {"type":"object","additionalProperties":false,"required":["documentId","filename","status","chunkCount","updatedAt"],"properties":{"documentId":{"type":"integer","minimum":1},"filename":{"type":"string","maxLength":255},"status":{"type":"string","const":"READY"},"chunkCount":{"type":"integer","minimum":1},"updatedAt":{"type":"string","format":"date-time"}}}
            """.strip();

    @Bean
    ToolDefinition knowledgeBaseCreateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.KNOWLEDGE_BASE_CREATE,
                "Create one knowledge base",
                "Creates one knowledge base owned by the authenticated user.",
                "Create one confirmed personal knowledge base without delete, batch or security configuration access.",
                mapper.readTree(CREATE_BASE_INPUT), mapper.readTree(CREATE_BASE_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("knowledge:search"), OwnershipPolicy.SELF,
                1, 8192, 10000);
    }

    @Bean
    ToolDefinition knowledgeBaseUpdateToolDefinition(ObjectMapper mapper) throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.KNOWLEDGE_BASE_UPDATE,
                "Update one knowledge base",
                "Updates bounded metadata and retrieval settings on one knowledge base owned by the authenticated user.",
                "Update one confirmed personal knowledge base without provider, model, delete, batch or security access.",
                mapper.readTree(UPDATE_BASE_INPUT), mapper.readTree(UPDATE_BASE_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("knowledge:search"), OwnershipPolicy.FIXED_RESOURCE,
                1, 8192, 10000);
    }

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

    @Bean
    ToolDefinition knowledgeDocumentReindexToolDefinition(ObjectMapper mapper)
            throws JsonProcessingException {
        return ToolDefinitionFactory.singleWrite(ToolCode.KNOWLEDGE_DOCUMENT_REINDEX,
                "Reindex one knowledge document",
                "Rebuilds embeddings for one knowledge document owned by the authenticated user.",
                "Reindex one confirmed document without model selection, upload, delete or batch access.",
                mapper.readTree(REINDEX_DOCUMENT_INPUT), mapper.readTree(REINDEX_DOCUMENT_OUTPUT),
                ToolWriteProfile.NON_RETRYABLE_L1, Set.of("knowledge:search"), OwnershipPolicy.FIXED_RESOURCE,
                1, 8192, 30000);
    }
}
