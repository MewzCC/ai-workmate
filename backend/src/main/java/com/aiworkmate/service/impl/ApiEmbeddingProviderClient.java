package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.service.EmbeddingProviderClient;
import com.aiworkmate.service.model.EmbeddingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ApiEmbeddingProviderClient implements EmbeddingProviderClient {

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    public ApiEmbeddingProviderClient(EmbeddingProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String provider() {
        return "api";
    }

    @Override
    public String model() {
        return properties.getApiModel();
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "EMBEDDING_API_KEY 尚未配置");
        }
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "向量化文本不能为空");
        }

        long startedAt = System.nanoTime();
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, properties.getBatchSize());
        try {
            for (int offset = 0; offset < texts.size(); offset += batchSize) {
                List<String> batch = texts.subList(offset, Math.min(texts.size(), offset + batchSize));
                ApiEmbeddingResponse response = restClient.post()
                        .uri(endpoint())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                        .body(requestBody(batch))
                        .retrieve()
                        .body(ApiEmbeddingResponse.class);
                appendValidated(vectors, response, batch.size());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Embedding API request failed, endpoint={}, model={}, textCount={}",
                    properties.getApiBaseUrl(), model(), texts.size(), ex);
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "远程向量模型 API 暂时不可用");
        } catch (RuntimeException ex) {
            log.error("Embedding API returned invalid response, model={}, textCount={}",
                    model(), texts.size(), ex);
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID);
        }
        log.info("Embedding API completed, model={}, textCount={}, dimension={}, latencyMs={}",
                model(), texts.size(), properties.getDimension(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return new EmbeddingResult(provider(), model(), List.copyOf(vectors));
    }

    private Map<String, Object> requestBody(List<String> texts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("input", texts);
        if (properties.isApiSendDimensions()) {
            body.put("dimensions", properties.getDimension());
        }
        return body;
    }

    private void appendValidated(List<float[]> target, ApiEmbeddingResponse response, int expectedCount) {
        if (response == null || response.data() == null || response.data().size() != expectedCount) {
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                    "远程向量模型返回数量不符合请求");
        }
        List<ApiEmbeddingItem> ordered = response.data().stream()
                .sorted(Comparator.comparingInt(ApiEmbeddingItem::index))
                .toList();
        for (int itemIndex = 0; itemIndex < ordered.size(); itemIndex++) {
            ApiEmbeddingItem item = ordered.get(itemIndex);
            if (item.index() != itemIndex) {
                throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                        "远程向量模型返回的索引不连续");
            }
            List<Double> values = item.embedding();
            if (values == null || values.size() != properties.getDimension()) {
                throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                        "远程向量模型返回维度不符合配置");
            }
            float[] vector = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                Double value = values.get(index);
                if (value == null || !Double.isFinite(value)) {
                    throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                            "远程向量模型返回了非法数值");
                }
                vector[index] = value.floatValue();
            }
            target.add(vector);
        }
    }

    private String endpoint() {
        String baseUrl = properties.getApiBaseUrl() == null
                ? "" : properties.getApiBaseUrl().strip().replaceAll("/+$", "");
        if (baseUrl.isBlank()) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "EMBEDDING_API_BASE_URL 尚未配置");
        }
        return baseUrl + "/embeddings";
    }

    private record ApiEmbeddingResponse(String model, List<ApiEmbeddingItem> data) {
    }

    private record ApiEmbeddingItem(int index, List<Double> embedding) {
    }
}
