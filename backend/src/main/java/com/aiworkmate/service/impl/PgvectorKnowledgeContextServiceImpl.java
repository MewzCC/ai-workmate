package com.aiworkmate.service.impl;

import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.dto.KnowledgeSearchItemResponse;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import com.aiworkmate.service.KnowledgeContextService;
import com.aiworkmate.service.KnowledgeService;
import com.aiworkmate.service.model.KnowledgeContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PgvectorKnowledgeContextServiceImpl implements KnowledgeContextService {

    private static final int MAX_PROMPT_CONTEXT_CHARS = 12_000;
    private static final int MAX_REFERENCE_TEXT_CHARS = 300;

    private final KnowledgeService knowledgeService;
    private final EmbeddingProperties properties;

    @Override
    public KnowledgeContext retrieve(Long userId, String userMessage, Long kbId) {
        KnowledgeSearchRequest request = new KnowledgeSearchRequest(userMessage,
                properties.getRetrievalTopK(), properties.getRetrievalMinScore());
        KnowledgeSearchResponse result = kbId == null
                ? knowledgeService.search(userId, request)
                : knowledgeService.searchInKnowledgeBase(userId, kbId, request);
        if (result.records().isEmpty()) {
            return KnowledgeContext.empty();
        }

        StringBuilder prompt = new StringBuilder();
        List<KnowledgeContext.Reference> references = new ArrayList<>();
        int referenceNumber = 1;
        for (KnowledgeSearchItemResponse item : result.records()) {
            String header = "[知识来源" + referenceNumber + "：" + item.filename()
                    + "，分块 " + item.chunkIndex() + "]\n";
            if (prompt.length() + header.length() >= MAX_PROMPT_CONTEXT_CHARS) {
                break;
            }
            int remaining = MAX_PROMPT_CONTEXT_CHARS - prompt.length() - header.length();
            String content = item.content().length() <= remaining
                    ? item.content() : item.content().substring(0, remaining);
            prompt.append(header).append(content).append("\n\n");
            references.add(new KnowledgeContext.Reference(
                    String.valueOf(item.docId()), String.valueOf(item.chunkId()),
                    item.filename(), item.score(), trimReferenceText(item.content())));
            referenceNumber++;
            if (content.length() < item.content().length()) {
                break;
            }
        }
        return references.isEmpty()
                ? KnowledgeContext.empty()
                : new KnowledgeContext(prompt.toString().strip(), List.copyOf(references));
    }

    private String trimReferenceText(String content) {
        if (content == null || content.isBlank()) return "";
        if (content.length() <= MAX_REFERENCE_TEXT_CHARS) return content;
        // 按 code point 截断，避免切断代理对产生孤立 surrogate
        return content.codePoints()
                .limit(MAX_REFERENCE_TEXT_CHARS)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
