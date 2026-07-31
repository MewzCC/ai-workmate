package com.aiworkmate.service;

import com.aiworkmate.service.model.EmbeddingDescriptor;
import com.aiworkmate.service.model.EmbeddingResult;

import java.util.List;

public interface EmbeddingService {

    EmbeddingResult embed(List<String> texts);

    EmbeddingDescriptor current();
}
