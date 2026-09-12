package com.aiworkmate.agent.tool.port;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ToolActorContextTest {
    @Test
    void keepsOnlyGatewayOwnedIdentityAndTraceFields() {
        var context = new ToolActorContext(91L, 7L, 10L, 20L, 1, "trace-1");

        assertThat(context.tenantId()).isEqualTo(91L);
        assertThat(context.userId()).isEqualTo(7L);
        assertThat(context.taskId()).isEqualTo(10L);
        assertThat(context.stepId()).isEqualTo(20L);
        assertThat(context.attempt()).isEqualTo(1);
        assertThat(context.traceId()).isEqualTo("trace-1");
    }

    @Test
    void rejectsInvalidTrustedValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolActorContext(0L, 7L, 10L, 20L, 1, "trace"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolActorContext(91L, 7L, 10L, 20L, -1, "trace"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ToolActorContext(91L, 7L, 10L, 20L, 1, " "));
    }
}
