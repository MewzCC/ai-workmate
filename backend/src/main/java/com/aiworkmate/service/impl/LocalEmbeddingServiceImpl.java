package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.service.EmbeddingProviderClient;
import com.aiworkmate.service.model.EmbeddingResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LocalEmbeddingServiceImpl implements EmbeddingProviderClient {

    private final EmbeddingProperties properties;
    private final RestClient restClient;

    public LocalEmbeddingServiceImpl(EmbeddingProperties properties) {
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
        return "local";
    }

    @Override
    public String model() {
        return properties.getLocalModel();
    }

    @Override
    public EmbeddingResult embed(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(text -> text == null || text.isBlank())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "向量化文本不能为空");
        }

        long startedAt = System.nanoTime();
        List<float[]> vectors = new ArrayList<>(texts.size());
        int batchSize = Math.max(1, properties.getBatchSize());
        try {
            for (int offset = 0; offset < texts.size(); offset += batchSize) {
                List<String> batch = texts.subList(offset, Math.min(texts.size(), offset + batchSize));
                EmbedResponse response = restClient.post()
                        .uri(endpoint(properties.getLocalBaseUrl(), "/embed"))
                        .body(new EmbedRequest(batch))
                        .retrieve()
                        .body(EmbedResponse.class);
                appendValidated(vectors, response, batch.size());
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.warn("Local embedding request failed, endpoint={}, textCount={}",
                    properties.getLocalBaseUrl(), texts.size(), ex);
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "无法连接本地向量模型，请确认 18080 服务已启动");
        } catch (RuntimeException ex) {
            log.error("Local embedding request failed unexpectedly, textCount={}", texts.size(), ex);
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID);
        }
        log.info("Local embedding completed, model={}, textCount={}, dimension={}, latencyMs={}",
                model(), texts.size(), properties.getDimension(),
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return new EmbeddingResult(provider(), model(), List.copyOf(vectors));
    }

    private void appendValidated(List<float[]> target, EmbedResponse response, int expectedCount) {
        if (response == null || response.embeddings() == null
                || response.embeddings().size() != expectedCount
                || response.dim() != properties.getDimension()) {
            throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                    "向量模型返回数量或维度不符合配置");
        }
        for (List<Double> values : response.embeddings()) {
            if (values == null || values.size() != properties.getDimension()) {
                throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                        "向量模型返回的向量维度不一致");
            }
            float[] vector = new float[values.size()];
            for (int index = 0; index < values.size(); index++) {
                Double value = values.get(index);
                if (value == null || !Double.isFinite(value)) {
                    throw new BusinessException(ErrorCode.EMBEDDING_RESPONSE_INVALID,
                            "向量模型返回了非法数值");
                }
                vector[index] = value.floatValue();
            }
            target.add(vector);
        }
    }

    private String endpoint(String baseUrl, String path) {
        String normalized = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        if (normalized.isBlank()) {
            throw new BusinessException(ErrorCode.EMBEDDING_UNAVAILABLE,
                    "EMBEDDING_LOCAL_BASE_URL 尚未配置");
        }
        return normalized + path;
    }

    private record EmbedRequest(List<String> texts) {
    }

    private record EmbedResponse(
            String model,
            String device,
            int dim,
            int count,
            boolean normalized,
            List<List<Double>> embeddings
    ) {
    }
}
