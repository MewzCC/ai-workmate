package com.aiworkmate.service.impl;

import com.aiworkmate.config.AiRuntimeProperties;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.config.OcrProperties;
import com.aiworkmate.service.EmbeddingService;
import com.aiworkmate.service.ObjectStorageService;
import com.aiworkmate.service.OcrService;
import com.aiworkmate.service.model.EmbeddingDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemCapabilityServiceImplTest {

    private final AiRuntimeProperties aiProperties = new AiRuntimeProperties();
    private final EmbeddingProperties embeddingProperties = new EmbeddingProperties();
    private final OcrProperties ocrProperties = new OcrProperties();
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final OcrService ocrService = mock(OcrService.class);
    private final ObjectStorageService storageService = mock(ObjectStorageService.class);
    private final RedisConnectionFactory redisFactory = mock(RedisConnectionFactory.class);

    @Test
    void shouldReturnSafeCapabilitySummary() {
        aiProperties.setApiKey("configured-secret");
        aiProperties.setBaseUrl("https://internal-ai.example");
        when(embeddingService.current()).thenReturn(new EmbeddingDescriptor("local", "model-a", 1024));
        when(ocrService.isAvailable()).thenReturn(true);
        when(storageService.isAvailable()).thenReturn(true);
        RedisConnection connection = mock(RedisConnection.class);
        when(redisFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        var response = service().inspect();

        assertThat(response.ai().status()).isEqualTo(SystemCapabilityServiceImpl.AVAILABLE);
        assertThat(response.embedding().summary()).containsEntry("model", "model-a");
        assertThat(response.ocr().available()).isTrue();
        assertThat(response.minio().available()).isTrue();
        assertThat(response.redis().available()).isTrue();
        assertThat(response.toString())
                .doesNotContain("configured-secret")
                .doesNotContain("internal-ai.example");
    }

    @Test
    void shouldReportDisabledAndUnavailableWithoutLeakingFailure() {
        aiProperties.setApiKey("development-only-unconfigured");
        embeddingProperties.setEnabled(false);
        ocrProperties.setEnabled(false);
        when(storageService.isAvailable()).thenThrow(new IllegalStateException("secret endpoint"));
        when(redisFactory.getConnection()).thenThrow(new IllegalStateException("redis://secret"));

        var response = service().inspect();

        assertThat(response.ai().status()).isEqualTo(SystemCapabilityServiceImpl.NOT_CONFIGURED);
        assertThat(response.embedding().status()).isEqualTo(SystemCapabilityServiceImpl.DISABLED);
        assertThat(response.ocr().status()).isEqualTo(SystemCapabilityServiceImpl.DISABLED);
        assertThat(response.minio().status()).isEqualTo(SystemCapabilityServiceImpl.UNAVAILABLE);
        assertThat(response.redis().status()).isEqualTo(SystemCapabilityServiceImpl.UNAVAILABLE);
        assertThat(response.toString()).doesNotContain("secret");
    }

    private SystemCapabilityServiceImpl service() {
        return new SystemCapabilityServiceImpl(
                aiProperties, embeddingProperties, ocrProperties,
                embeddingService, ocrService, storageService, redisFactory);
    }
}
