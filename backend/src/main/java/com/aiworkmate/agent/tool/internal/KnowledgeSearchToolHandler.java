package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.agent.tool.port.KnowledgeToolPort;
import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public final class KnowledgeSearchToolHandler
        extends TypedReadToolHandler<KnowledgeToolPort.Query, KnowledgeToolPort.Result> {
    private final KnowledgeToolPort knowledgeToolPort;

    public KnowledgeSearchToolHandler(KnowledgeToolPort knowledgeToolPort, ObjectMapper objectMapper) {
        super(ToolCode.KNOWLEDGE_SEARCH, objectMapper);
        this.knowledgeToolPort = knowledgeToolPort;
    }

    @Override protected KnowledgeToolPort.Query parseArguments(JsonNode arguments) {
        String query = arguments.path("query").asText("").strip();
        if (query.isEmpty()) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        int topK = arguments.has("topK") ? arguments.path("topK").asInt(0) : 5;
        if (topK < 1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        topK = Math.min(10, topK);
        Double minScore = arguments.has("minScore") ? arguments.path("minScore").asDouble() : null;
        return new KnowledgeToolPort.Query(query, topK, minScore);
    }

    @Override protected KnowledgeToolPort.Result invoke(
            TrustedToolContext context, KnowledgeToolPort.Query query) {
        return knowledgeToolPort.search(context.userId(), query);
    }

    @Override protected JsonNode serializeResult(KnowledgeToolPort.Result result) {
        ObjectNode output = objectMapper().createObjectNode();
        ArrayNode items = output.putArray("items");
        result.items().stream().limit(10).forEach(record -> {
            ObjectNode item = items.addObject();
            item.put("content", record.content());
            item.put("score", record.score());
            item.put("matchType", record.matchType());
            ObjectNode citation = item.putObject("citation");
            citation.put("documentId", record.documentId());
            citation.put("chunkId", record.chunkId());
            citation.put("filename", record.filename());
            citation.put("chunkIndex", record.chunkIndex());
        });
        output.put("untrustedContent", true);
        output.put("usagePolicy", "DISPLAY_OR_SUMMARIZE_ONLY");
        return output;
    }
}
