package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.EmbeddingStatusResponse;
import com.aiworkmate.dto.KnowledgeDocumentBatchRequest;
import com.aiworkmate.dto.KnowledgeDocumentCreateRequest;
import com.aiworkmate.dto.KnowledgeDocumentDetailResponse;
import com.aiworkmate.dto.KnowledgeDocumentResponse;
import com.aiworkmate.dto.KnowledgeSearchRequest;
import com.aiworkmate.dto.KnowledgeSearchResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeService {

    KnowledgeDocumentResponse create(Long userId, KnowledgeDocumentCreateRequest request);

    KnowledgeDocumentResponse upload(Long userId, Long kbId, MultipartFile file);

    KnowledgeDocumentResponse reindex(Long userId, Long documentId);

    KnowledgeDocumentDetailResponse documentDetail(Long userId, Long documentId);

    void deleteChunk(Long userId, Long documentId, Long chunkId);

    int batchDelete(Long userId, List<Long> ids);

    List<KnowledgeDocumentResponse> batchReindex(Long userId, List<Long> ids);

    PageResponse<KnowledgeDocumentResponse> list(Long userId, Long kbId, int page, int size);

    void delete(Long userId, Long documentId);

    KnowledgeSearchResponse search(Long userId, KnowledgeSearchRequest request);

    KnowledgeSearchResponse searchInKnowledgeBase(Long userId, Long kbId, KnowledgeSearchRequest request);

    EmbeddingStatusResponse embeddingStatus();
}
