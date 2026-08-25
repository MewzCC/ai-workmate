package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.service.KnowledgeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class KnowledgeSearchToolHandler implements ToolHandler {
    private final KnowledgeService knowledgeService;
    private final ObjectMapper objectMapper;

    @Override
    public String toolCode() { return "knowledge.search"; }

    @Override
    public String handlerVersion() { return "1.0.0"; }

    @Override
    public JsonNode execute(TrustedToolContext context, JsonNode arguments) {
        String query = arguments.path("query").asText("").strip();
        if (query.isEmpty()) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        int topK = arguments.has("topK") ? arguments.path("topK").asInt(0) : 5;
        if (topK < 1) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        topK = Math.min(10, topK);
        Double minScore = arguments.has("minScore") ? arguments.path("minScore").asDouble() : null;
        KnowledgeSearchResponse result = knowledgeService.search(
                context.userId(), new KnowledgeSearchRequest(query, topK, minScore));

        ObjectNode output = objectMapper.createObjectNode();
        ArrayNode items = output.putArray("items");
        result.records().stream().limit(10).forEach(record -> {
            ObjectNode item = items.addObject();
            item.put("content", record.content());
            item.put("score", record.score());
            item.put("matchType", record.matchType());
            ObjectNode citation = item.putObject("citation");
            citation.put("documentId", record.docId());
            citation.put("chunkId", record.chunkId());
            citation.put("filename", record.filename());
            citation.put("chunkIndex", record.chunkIndex());
        });
        output.put("untrustedContent", true);
        output.put("usagePolicy", "DISPLAY_OR_SUMMARIZE_ONLY");
        return output;
    }
}
