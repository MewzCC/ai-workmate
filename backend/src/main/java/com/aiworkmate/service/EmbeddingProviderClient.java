package com.aiworkmate.service;

import com.aiworkmate.service.model.EmbeddingResult;

import java.util.List;

public interface EmbeddingProviderClient {

    String provider();

    String model();

    EmbeddingResult embed(List<String> texts);
}
