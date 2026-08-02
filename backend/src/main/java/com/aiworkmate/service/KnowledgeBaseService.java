package com.aiworkmate.service;

import com.aiworkmate.dto.KnowledgeBaseCreateRequest;
import com.aiworkmate.dto.KnowledgeBaseResponse;
import com.aiworkmate.dto.KnowledgeBaseUpdateRequest;

import java.util.List;

public interface KnowledgeBaseService {

    List<KnowledgeBaseResponse> list(Long userId);

    KnowledgeBaseResponse create(Long userId, KnowledgeBaseCreateRequest request);

    KnowledgeBaseResponse detail(Long userId, Long kbId);

    KnowledgeBaseResponse update(Long userId, Long kbId, KnowledgeBaseUpdateRequest request);

    void delete(Long userId, Long kbId);
}
