package com.aiworkmate.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class ProductionEnvironmentValidator implements InitializingBean {

    private static final String DEFAULT_JWT_SECRET =
            "YWktd29ya21hdGUtand0LXNlY3JldC1rZXktcGxlYXNlLWNoYW5nZS1pbi1wcm9kdWN0aW9u";

    private final Environment environment;
    private final String jwtSecret;
    private final String databasePassword;
    private final String aiApiKey;

    public ProductionEnvironmentValidator(
            Environment environment,
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${spring.datasource.password}") String databasePassword,
            @Value("${spring.ai.openai.api-key}") String aiApiKey) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.databasePassword = databasePassword;
        this.aiApiKey = aiApiKey;
    }

    @Override
    public void afterPropertiesSet() {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            return;
        }
        if (DEFAULT_JWT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET must be configured for production");
        }
        if ("postgres".equals(databasePassword)) {
            throw new IllegalStateException("DB_PASSWORD must be configured for production");
        }
        if (aiApiKey == null || aiApiKey.isBlank() || "development-only-unconfigured".equals(aiApiKey)) {
            throw new IllegalStateException("AI_API_KEY must be configured for production");
        }
    }
}
