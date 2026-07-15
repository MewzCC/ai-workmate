package com.aiworkmate.service;

import com.aiworkmate.service.model.KnowledgeContext;

public interface KnowledgeContextService {

    KnowledgeContext retrieve(Long userId, String userMessage);
}
