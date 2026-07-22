package com.aiworkmate.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionEnvironmentValidatorTest {

    private static final String DEFAULT_SECRET =
            "YWktd29ya21hdGUtand0LXNlY3JldC1rZXktcGxlYXNlLWNoYW5nZS1pbi1wcm9kdWN0aW9u";

    @Test
    void shouldRejectDefaultSecretInProduction() {
        var validator = new ProductionEnvironmentValidator(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                DEFAULT_SECRET,
                "secure-password",
                "configured-ai-key"
        );

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void shouldAllowDevelopmentDefaults() {
        var validator = new ProductionEnvironmentValidator(
                new MockEnvironment().withProperty("spring.profiles.active", "dev"),
                DEFAULT_SECRET,
                "postgres",
                "development-only-unconfigured"
        );

        assertThatCode(validator::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectMissingAiKeyInProduction() {
        var validator = new ProductionEnvironmentValidator(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                "a-secure-random-jwt-secret-that-is-not-default",
                "secure-password",
                "development-only-unconfigured"
        );

        assertThatThrownBy(validator::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AI_API_KEY");
    }
}
