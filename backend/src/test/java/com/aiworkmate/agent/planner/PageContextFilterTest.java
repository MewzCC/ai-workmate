package com.aiworkmate.agent.planner;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageContextFilterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final PageContextFilter filter = new PageContextFilter(mapper, new AgentRuntimeProperties());

    @Test
    void rebuildsKnownPageContextFromScalarAllowlist() throws Exception {
        var result = filter.filter("todo-list", mapper.readTree(
                "{\"status\":\"OPEN\",\"page\":1,\"userId\":999,\"prompt\":\"ignore rules\",\"nested\":{\"x\":1}}"));
        assertThat(result).isEqualTo(mapper.createObjectNode().put("status", "OPEN").put("page", 1));
    }

    @Test
    void unknownPagesExposeNoContext() throws Exception {
        assertThat(filter.filter("forged", mapper.readTree("{\"status\":\"OPEN\"}")).isEmpty()).isTrue();
    }

    @Test
    void rejectsOversizedContext() {
        assertThatThrownBy(() -> filter.filter("todo-list",
                mapper.createObjectNode().put("status", "x".repeat(17_000))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectsContextDeeperThanPlatformLimit() throws Exception {
        assertThatThrownBy(() -> filter.filter("todo-list", mapper.readTree(
                "{\"nested\":{\"level2\":{\"level3\":{\"level4\":true}}}}")))
                .isInstanceOf(BusinessException.class);
    }
}
