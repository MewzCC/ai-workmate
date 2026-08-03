package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.RerankProperties;
import com.aiworkmate.service.RerankService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 硅基流动（SiliconFlow）rerank 实现。
 * 端点：POST {base}/rerank，请求 {model, query, documents, top_n}，
 * 响应 results[] 为按相关性降序的 {index, relevance_score}。
 */
@Slf4j
@Service
public class SiliconflowRerankService implements RerankService {

    private final RerankProperties rerankProperties;
    private final EmbeddingProperties embeddingProperties;
    private final RestClient restClient;

    public SiliconflowRerankService(RerankProperties rerankProperties, EmbeddingProperties embeddingProperties) {
        this.rerankProperties = rerankProperties;
        this.embeddingProperties = embeddingProperties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(rerankProperties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(rerankProperties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public boolean configured() {
        return rerankProperties.isEnabled()
                && model() != null && !model().isBlank()
                && apiKey() != null && !apiKey().isBlank();
    }

    @Override
    public List<RankedItem> rerank(String query, List<String> documents, int topN) {
        if (!configured()) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE, "重排服务未配置");
        }
        if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "重排的查询与文档不能为空");
        }
        long startedAt = System.nanoTime();
        try {
            RerankResponse response = restClient.post()
                    .uri(endpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey())
                    .body(Map.of(
                            "model", model(),
                            "query", query,
                            "documents", documents,
                            "top_n", Math.max(1, topN)))
                    .retrieve()
                    .body(RerankResponse.class);
            if (response == null || response.results() == null) {
                throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID, "重排服务返回为空");
            }
            List<RankedItem> ranked = response.results().stream()
                    .map(item -> new RankedItem(item.index(), item.relevanceScore()))
                    .toList();
            log.info("Rerank completed, model={}, documents={}, latencyMs={}",
                    model(), documents.size(), Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            return ranked;
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Rerank API request failed, endpoint={}, model={}, documentCount={}",
                    rerankProperties.getApiBaseUrl(), model(), documents.size(), ex);
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE, "重排服务暂时不可用");
        } catch (RuntimeException ex) {
            log.error("Rerank API returned invalid response, model={}, documentCount={}",
                    model(), documents.size(), ex);
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID);
        }
    }

    private String model() {
        return rerankProperties.getModel();
    }

    private String apiKey() {
        if (rerankProperties.getApiKey() != null && !rerankProperties.getApiKey().isBlank()) {
            return rerankProperties.getApiKey();
        }
        // 未单独配置 RERANK_API_KEY 时复用 Embedding API Key（硅基流动同一密钥）
        return embeddingProperties.getApiKey();
    }

    private String endpoint() {
        String baseUrl = rerankProperties.getApiBaseUrl() == null
                ? "" : rerankProperties.getApiBaseUrl().strip().replaceAll("/+$", "");
        if (baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE, "RERANK_API_BASE_URL 尚未配置");
        }
        return baseUrl + "/rerank";
    }

    private record RerankResponse(String id, List<RerankResult> results) {
    }

    private record RerankResult(int index, @JsonProperty("relevance_score") double relevanceScore) {
    }
}
