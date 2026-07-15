package com.aiworkmate.service.impl;

import com.aiworkmate.service.KnowledgeContextService;
import com.aiworkmate.service.model.KnowledgeContext;
import org.springframework.stereotype.Service;

@Service
public class EmptyKnowledgeContextServiceImpl implements KnowledgeContextService {

    @Override
    public KnowledgeContext retrieve(Long userId, String userMessage) {
        return KnowledgeContext.empty();
    }
}
