package com.aiworkmate.service.impl;

import com.aiworkmate.config.AiProviderRuntime;
import com.aiworkmate.dto.AiModelCategory;
import com.aiworkmate.service.RerankService.RankedItem;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.EmbeddingResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChannelKnowledgeModels {
    static final String PROVIDER = "channel";
    private final AiProviderRuntime runtime;
    private final ApiEmbeddingProviderClient embedding;
    private final SiliconflowRerankService rerank;

    EmbeddingDescriptor descriptor(String reference) {
        runtime.validateTypedModel(reference, AiModelCategory.EMBEDDING);
        return new EmbeddingDescriptor(PROVIDER, reference, ApiEmbeddingProviderClient.STORAGE_DIMENSION);
    }

    EmbeddingResult embed(String reference, List<String> texts) {
        return embedding.embed(texts, reference, runtime.channelModel(reference, AiModelCategory.EMBEDDING));
    }

    void validateRerank(String reference) {
        runtime.validateTypedModel(reference, AiModelCategory.RERANK);
    }

    List<RankedItem> rerank(String reference, String query, List<String> documents, int limit) {
        return rerank.rerank(query, documents, limit, runtime.channelModel(reference, AiModelCategory.RERANK));
    }
}
