package com.aiworkmate.service.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderExceptionTranslatorTest {

    @Test
    void shouldExplainRejectedApiKeyWithoutLeakingProviderResponse() {
        WebClientResponseException error = WebClientResponseException.create(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );

        assertThat(AiProviderExceptionTranslator.translate(error).getMessage())
                .isEqualTo("DeepSeek API Key 无效或已失效，请检查服务端配置");
    }
}
