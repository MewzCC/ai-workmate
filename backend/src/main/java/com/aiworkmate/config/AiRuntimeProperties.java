package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.openai")
public class AiRuntimeProperties {

    @lombok.ToString.Exclude
    private String apiKey;
    private String baseUrl;

    @org.springframework.beans.factory.annotation.Autowired
    @lombok.ToString.Exclude
    private org.springframework.beans.factory.ObjectProvider<AiProviderRuntime> runtime;

    public boolean configured() {
        return runtime != null && runtime.getIfAvailable() != null ? runtime.getObject().configured() : environmentConfigured();
    }

    public boolean environmentConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"development-only-unconfigured".equals(apiKey);
    }
}
