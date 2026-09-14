package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "app.integration")
public class IntegrationUpstreamProperties {
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 15;
    private int maxResponseBytes = 65536;
    private Map<String, Upstream> upstreams = new LinkedHashMap<>();

    @Data
    public static class Upstream {
        private String displayName;
        private String baseUrl;
        private boolean enabled;
        private boolean sandbox = true;
        private boolean allowInsecureHttp;
        private String authHeaderName;
        private String authHeaderValue;
    }
}
