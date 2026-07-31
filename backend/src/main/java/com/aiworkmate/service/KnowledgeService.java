package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.EmbeddingStatusResponse;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.dto.KnowledgeDocumentResponse;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.KnowledgeSearchResponse;

public interface KnowledgeService {

    KnowledgeDocumentResponse create(Long userId, KnowledgeDocumentCreateRequest request);

    KnowledgeDocumentResponse reindex(Long userId, Long documentId);

    PageResponse<KnowledgeDocumentResponse> list(Long userId, int page, int size);

    void delete(Long userId, Long documentId);

    KnowledgeSearchResponse search(Long userId, KnowledgeSearchRequest request);

    EmbeddingStatusResponse embeddingStatus();
}
