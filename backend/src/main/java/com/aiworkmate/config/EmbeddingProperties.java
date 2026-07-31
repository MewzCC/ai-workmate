package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.embedding")
public class EmbeddingProperties {

    private boolean enabled = true;
    private String provider = "local";
    private String localBaseUrl = "http://127.0.0.1:18080";
    private String localModel = "Qwen3-Embedding-0.6B";
    private String apiBaseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private String apiModel = "text-embedding-3-small";
    private boolean apiSendDimensions = true;
    private int dimension = 1024;
    private int batchSize = 16;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 60000;
    private int chunkMaxChars = 1000;
    private int chunkOverlapChars = 120;
    private int maxDocumentChars = 120000;
    private int retrievalTopK = 5;
    private double retrievalMinScore = 0.35;
}
