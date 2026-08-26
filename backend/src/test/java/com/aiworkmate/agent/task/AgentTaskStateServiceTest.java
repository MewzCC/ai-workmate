package com.aiworkmate.agent.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskStateServiceTest {
    private final AgentTaskMapper mapper = mock(AgentTaskMapper.class);
    private final AgentTaskStateService service = new AgentTaskStateService(mapper);

    @Test
    void shouldApplyStateAndVersionOnlyAfterConditionalUpdateWins() {
        AgentTask task = task(10L, AgentTaskStatus.PLAN_READY, 4L);
        when(mapper.transition(10L, "PLAN_READY", "QUEUED", 4L)).thenReturn(1);

        assertThat(service.transition(task, AgentTaskStatus.QUEUED)).isTrue();
        assertThat(task.getStatus()).isEqualTo("QUEUED");
        assertThat(task.getVersion()).isEqualTo(5L);
    }

    @Test
    void shouldLeaveSnapshotUnchangedWhenConcurrentUpdateAlreadyWon() {
        AgentTask task = task(10L, AgentTaskStatus.PLAN_READY, 4L);
        when(mapper.transition(10L, "PLAN_READY", "CANCELLED", 4L)).thenReturn(0);

        assertThat(service.transition(task, AgentTaskStatus.CANCELLED)).isFalse();
        assertThat(task.getStatus()).isEqualTo("PLAN_READY");
        assertThat(task.getVersion()).isEqualTo(4L);
        verify(mapper).transition(10L, "PLAN_READY", "CANCELLED", 4L);
    }

    private AgentTask task(Long id, AgentTaskStatus status, Long version) {
        AgentTask task = new AgentTask();
        task.setId(id);
        task.setStatus(status.name());
        task.setVersion(version);
        return task;
    }
}
