package com.aiworkmate.service.impl;

import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.OcrProperties;
import com.aiworkmate.dto.SystemCapabilitiesResponse;
import com.aiworkmate.dto.SystemCapabilityStatusResponse;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.ObjectStorageService;
import com.aiworkmate.service.OcrService;
import com.aiworkmate.service.SystemCapabilityService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemCapabilityServiceImpl implements SystemCapabilityService {

    static final String AVAILABLE = "AVAILABLE";
    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String DISABLED = "DISABLED";
    static final String NOT_CONFIGURED = "NOT_CONFIGURED";

    private final AiRuntimeProperties aiProperties;
    private final EmbeddingProperties embeddingProperties;
    private final OcrProperties ocrProperties;
    private final EmbeddingService embeddingService;
    private final OcrService ocrService;
    private final ObjectStorageService objectStorageService;
    private final RedisConnectionFactory redisConnectionFactory;

    @Override
    public SystemCapabilitiesResponse inspect() {
        return new SystemCapabilitiesResponse(
                Instant.now(),
                aiStatus(),
                embeddingStatus(),
                ocrStatus(),
                dependencyStatus("minio", objectStorageService::isAvailable),
                dependencyStatus("redis", this::redisAvailable)
        );
    }

    private SystemCapabilityStatusResponse aiStatus() {
        boolean configured = aiProperties.configured();
        return status(true, configured, configured ? AVAILABLE : NOT_CONFIGURED,
                Map.of("provider", "openai-compatible", "configuration", "server-managed"));
    }

    private SystemCapabilityStatusResponse embeddingStatus() {
        if (!embeddingProperties.isEnabled()) {
            return status(false, false, DISABLED, Map.of("provider", safe(embeddingProperties.getProvider())));
        }
        try {
            EmbeddingDescriptor descriptor = embeddingService.current();
            return status(true, true, AVAILABLE, Map.of(
                    "provider", safe(descriptor.provider()),
                    "model", safe(descriptor.model()),
                    "dimension", Integer.toString(descriptor.dimension())
            ));
        } catch (RuntimeException ex) {
            log.warn("Embedding capability inspection failed: {}", ex.getClass().getSimpleName());
            return status(true, false, UNAVAILABLE,
                    Map.of("provider", safe(embeddingProperties.getProvider())));
        }
    }

    private SystemCapabilityStatusResponse ocrStatus() {
        if (!ocrProperties.isEnabled()) {
            return status(false, false, DISABLED, Map.of("engine", "paddleocr-http"));
        }
        return dependencyStatus("paddleocr-http", ocrService::isAvailable);
    }

    private SystemCapabilityStatusResponse dependencyStatus(String provider, AvailabilityProbe probe) {
        boolean available = false;
        try {
            available = probe.available();
        } catch (RuntimeException ex) {
            log.warn("Capability inspection failed, provider={}, cause={}",
                    provider, ex.getClass().getSimpleName());
        }
        return status(true, available, available ? AVAILABLE : UNAVAILABLE,
                Map.of("provider", provider));
    }

    private boolean redisAvailable() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            return connection.ping() != null;
        }
    }

    private SystemCapabilityStatusResponse status(boolean enabled, boolean available,
                                                   String status, Map<String, String> summary) {
        return new SystemCapabilityStatusResponse(enabled, available, status, Map.copyOf(summary));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "unknown" : value.strip();
    }

    @FunctionalInterface
    private interface AvailabilityProbe {
        boolean available();
    }
}
