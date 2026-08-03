package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 检索结果重排（rerank）配置，默认对接硅基流动（SiliconFlow）rerank API。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.embedding.rerank")
public class RerankProperties {

    private boolean enabled = false;

    private String model = "Qwen/Qwen3-Reranker-4B";

    private String apiBaseUrl = "https://api.siliconflow.cn/v1";

    /** 未配置时复用 Embedding API Key */
    private String apiKey = "";

    private int connectTimeoutMs = 3000;

    private int readTimeoutMs = 30000;

    private int topN = 5;
}
