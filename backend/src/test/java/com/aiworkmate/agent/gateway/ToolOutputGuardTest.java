package com.aiworkmate.agent.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolOutputGuardTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolOutputGuard guard = new ToolOutputGuard();

    @Test
    void shouldRejectNestedSecretsAndExecutableLinks() throws Exception {
        assertThat(guard.safe(objectMapper.readTree("{\"nested\":{\"authorization\":\"bearer x\"}}"))).isFalse();
        assertThat(guard.safe(objectMapper.readTree("{\"link\":\" javascript:alert(1)\"}"))).isFalse();
    }

    @Test
    void shouldAllowPlainStructuredData() throws Exception {
        assertThat(guard.safe(objectMapper.readTree("{\"items\":[{\"title\":\"Todo\"}]}"))).isTrue();
    }

    @Test
    void shouldEnforceArrayLimitsRecursively() throws Exception {
        assertThat(guard.withinArrayLimit(objectMapper.readTree("{\"items\":[1,2,3]}"), 2)).isFalse();
        assertThat(guard.withinArrayLimit(objectMapper.readTree("{\"items\":[1,2]}"), 2)).isTrue();
    }
}
