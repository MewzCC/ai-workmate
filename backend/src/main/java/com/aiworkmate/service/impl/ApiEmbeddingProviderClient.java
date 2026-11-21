package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.AiProviderRuntime.ChannelModel;
import com.aiworkmate.service.EmbeddingProviderClient;
import com.aiworkmate.service.model.EmbeddingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ApiEmbeddingProviderClient implements EmbeddingProviderClient {

    static final int STORAGE_DIMENSION = 1024;
    private final EmbeddingProperties properties;
    private final RestClient restClient;

    public ApiEmbeddingProviderClient(EmbeddingProperties properties) {
        this.properties = properties;
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs())).build());
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
        return embed(texts, provider(), model(), model(), endpoint(), properties.getApiKey(),
                properties.getDimension(), properties.isApiSendDimensions());
    }

    EmbeddingResult embed(List<String> texts, String reference, ChannelModel selected) {
        return embed(texts, "channel", reference, selected.model().id(), selected.baseUrl() + "/embeddings",
                selected.apiKey(), STORAGE_DIMENSION, selected.model().sendDimensions());
    }

    private EmbeddingResult embed(List<String> texts, String provider, String reference, String upstreamModel,
                                  String endpoint, String apiKey, int dimension, boolean sendDimensions) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "validation.runtime.embedding.keyMissing");
        }
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.runtime.embedding.textEmpty");
        }

        long startedAt = System.nanoTime();
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, properties.getBatchSize());
        try {
            for (int offset = 0; offset < texts.size(); offset += batchSize) {
                List<String> batch = texts.subList(offset, Math.min(texts.size(), offset + batchSize));
                ApiEmbeddingResponse response = restClient.post()
                        .uri(endpoint)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .body(requestBody(batch, upstreamModel, dimension, sendDimensions))
                        .retrieve()
                        .body(ApiEmbeddingResponse.class);
                appendValidated(vectors, response, batch.size(), dimension);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Embedding API request failed, textCount={}, errorType={}", texts.size(), ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "validation.runtime.embedding.remoteUnavailable");
        } catch (RuntimeException ex) {
            log.warn("Embedding API returned invalid response, textCount={}, errorType={}", texts.size(), ex.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID);
        }
        log.info("Embedding API completed, model={}, textCount={}, dimension={}, latencyMs={}",
                reference, texts.size(), dimension,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return new EmbeddingResult(provider, reference, List.copyOf(vectors));
    }

    private Map<String, Object> requestBody(List<String> texts, String model, int dimension, boolean sendDimensions) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        if (sendDimensions) {
            body.put("dimensions", dimension);
        }
        return body;
    }

    private void appendValidated(List<float[]> target, ApiEmbeddingResponse response, int expectedCount, int dimension) {
        if (response == null || response.data() == null || response.data().size() != expectedCount) {
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                    "validation.runtime.embedding.remoteCountInvalid");
        }
        List<ApiEmbeddingItem> ordered = response.data().stream()
                .sorted(Comparator.comparingInt(ApiEmbeddingItem::index))
                .toList();
        for (int itemIndex = 0; itemIndex < ordered.size(); itemIndex++) {
            ApiEmbeddingItem item = ordered.get(itemIndex);
            if (item.index() != itemIndex) {
                throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                        "validation.runtime.embedding.remoteIndicesInvalid");
            }
            List<Double> values = item.embedding();
            if (values == null || values.size() != dimension) {
                throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                        "validation.runtime.embedding.remoteDimensionInvalid");
            }
            float[] vector = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                Double value = values.get(index);
                if (value == null || !Float.isFinite(value.floatValue())) {
                    throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                            "validation.runtime.embedding.remoteValuesInvalid");
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
                    "validation.runtime.embedding.remoteEndpointMissing");
        }
        return baseUrl + "/embeddings";
    }

    private record ApiEmbeddingResponse(String model, List<ApiEmbeddingItem> data) {
    }

    private record ApiEmbeddingItem(int index, List<Double> embedding) {
    }
}
