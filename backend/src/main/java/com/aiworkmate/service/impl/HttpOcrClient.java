package com.aiworkmate.service.impl;

import com.aiworkmate.config.OcrProperties;
import com.aiworkmate.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class HttpOcrClient implements OcrService {

    private final OcrProperties properties;
    private final RestClient restClient;
    private final AtomicBoolean unavailableLogged = new AtomicBoolean(false);

    public HttpOcrClient(OcrProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String recognize(Path imageFile, String filename) {
        if (!properties.isEnabled()) {
            return unavailable("OCR 未启用（APP_OCR_ENABLED=false）");
        }
        long startedAt = System.nanoTime();
        try {
            OcrResponse response = restClient.post()
                    .uri(endpoint() + "/ocr/recognize")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .headers(headers -> {
                        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
                            headers.set("X-API-Key", properties.getApiKey());
                        }
                    })
                    .body(new FileSystemResource(imageFile))
                    .retrieve()
                    .body(OcrResponse.class);
            if (response == null) {
                return unavailable("OCR 服务返回空响应");
            }
            String text = response.text() == null ? "" : response.text().strip();
            log.info("OCR completed, filename={}, chars={}, blocks={}, engine={}, latencyMs={}",
                    filename, text.length(), response.blocks().size(), response.engine(),
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
            return text.isBlank() ? null : text;
        } catch (RestClientResponseException ex) {
            log.warn("OCR service returned error status={}, filename={}", ex.getStatusCode(), filename);
            return unavailable("OCR 服务返回错误状态 " + ex.getStatusCode());
        } catch (RestClientException ex) {
            log.warn("OCR service request failed, filename={}", filename, ex);
            return unavailable("OCR 服务请求失败");
        } catch (RuntimeException ex) {
            log.warn("OCR service response invalid, filename={}", filename, ex);
            return unavailable("OCR 服务响应解析失败");
        }
    }

    @Override
    public boolean isAvailable() {
        if (!properties.isEnabled()) return false;
        try {
            restClient.get().uri(endpoint() + "/healthz").retrieve().toBodilessEntity();
            unavailableLogged.set(false);
            return true;
        } catch (RestClientException ex) {
            return false;
        }
    }

    private String unavailable(String reason) {
        if (unavailableLogged.compareAndSet(false, true)) {
            log.warn("OCR unavailable: {}, baseUrl={}", reason, properties.getBaseUrl());
        }
        return null;
    }

    private String endpoint() {
        String baseUrl = properties.getBaseUrl() == null
                ? "" : properties.getBaseUrl().strip().replaceAll("/+$", "");
        return baseUrl.isBlank() ? "" : baseUrl;
    }

    private record OcrResponse(String text, List<OcrBlock> blocks, String language, String engine) {
        private OcrResponse {
            blocks = blocks == null ? List.of() : blocks;
        }
    }

    private record OcrBlock(String text, double confidence, List<Number> box) {
    }
}
