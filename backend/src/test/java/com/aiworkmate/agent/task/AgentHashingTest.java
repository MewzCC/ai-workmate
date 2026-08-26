package com.aiworkmate.agent.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentHashingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentHashing hashing = new AgentHashing(objectMapper);

    @Test
    void shouldHashObjectsIndependentlyOfPropertyOrder() throws Exception {
        String first = hashing.hash(objectMapper.readTree("{\"b\":2,\"a\":1}"));
        String second = hashing.hash(objectMapper.readTree("{\"a\":1,\"b\":2}"));

        assertThat(first).isEqualTo(second).startsWith("sha256:");
    }

    @Test
    void shouldPreserveArrayOrder() throws Exception {
        assertThat(hashing.hash(objectMapper.readTree("[1,2]")))
                .isNotEqualTo(hashing.hash(objectMapper.readTree("[2,1]")));
    }
}
