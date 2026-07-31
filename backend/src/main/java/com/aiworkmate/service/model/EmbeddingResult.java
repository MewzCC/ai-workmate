package com.aiworkmate.service.model;

import java.util.List;

public record EmbeddingResult(String provider, String model, List<float[]> vectors) {
}
