package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.EmbeddingProperties;
import com.aiworkmate.service.EmbeddingProviderClient;
import com.aiworkmate.service.model.EmbeddingResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurableEmbeddingServiceImplTest {

    @Test
    void shouldSelectConfiguredProviderWithoutFallback() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setProvider("api");
        EmbeddingProviderClient local = client("local", "local-model", 1F);
        EmbeddingProviderClient api = client("api", "api-model", 2F);

        ConfigurableEmbeddingServiceImpl service =
                new ConfigurableEmbeddingServiceImpl(properties, List.of(local, api));

        assertThat(service.current().provider()).isEqualTo("api");
        assertThat(service.current().model()).isEqualTo("api-model");
        assertThat(service.embed(List.of("query")).vectors().get(0))
                .containsExactly(2F, 2F, 2F);
    }

    @Test
    void shouldRejectUnsupportedOrDisabledProvider() {
        EmbeddingProperties properties = new EmbeddingProperties();
        ConfigurableEmbeddingServiceImpl service =
                new ConfigurableEmbeddingServiceImpl(properties,
                        List.of(client("local", "model", 1F)));

        properties.setProvider("unknown");
        assertThatThrownBy(service::current).isInstanceOf(BusinessException.class);

        properties.setProvider("local");
        properties.setEnabled(false);
        assertThatThrownBy(() -> service.embed(List.of("query")))
                .isInstanceOf(BusinessException.class);
    }

    private EmbeddingProviderClient client(String provider, String model, float value) {
        return new EmbeddingProviderClient() {
            @Override
            public String provider() {
                return provider;
            }

            @Override
            public String model() {
                return model;
            }

            @Override
            public EmbeddingResult embed(List<String> texts) {
                return new EmbeddingResult(provider, model,
                        texts.stream().map(ignored -> new float[]{value, value, value}).toList());
            }
        };
    }
}
