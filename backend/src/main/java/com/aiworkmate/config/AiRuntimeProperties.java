package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "spring.ai.openai")
public class AiRuntimeProperties {

    private String apiKey;
    private String baseUrl;

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank() && !"development-only-unconfigured".equals(apiKey);
    }
}
