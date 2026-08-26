package com.aiworkmate.agent.task;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentIdempotencyServiceTest {
    private final AgentIdempotencyMapper mapper = mock(AgentIdempotencyMapper.class);
    private final AgentIdempotencyService service = new AgentIdempotencyService(mapper);

    @Test
    void shouldBindNewDomainKey() {
        when(mapper.insertIfAbsent(1L, 7L, "PLAN", "plan-key-123", "sha256:a", 99L)).thenReturn(1);

        assertThat(service.bind(1L, 7L, IdempotencyOperation.PLAN, "plan-key-123", "sha256:a", 99L))
                .isEqualTo(new IdempotencyBinding(true, 99L));
    }

    @Test
    void shouldReturnExistingTaskForSameRequest() {
        when(mapper.insertIfAbsent(1L, 7L, "PLAN", "plan-key-123", "sha256:a", 99L)).thenReturn(0);
        AgentIdempotency existing = new AgentIdempotency();
        existing.setRequestHash("sha256:a");
        existing.setTaskId(42L);
        when(mapper.selectDomainKey(1L, 7L, "PLAN", "plan-key-123")).thenReturn(existing);

        assertThat(service.bind(1L, 7L, IdempotencyOperation.PLAN, "plan-key-123", "sha256:a", 99L))
                .isEqualTo(new IdempotencyBinding(false, 42L));
    }

    @Test
    void shouldRejectSameKeyForDifferentRequest() {
        when(mapper.insertIfAbsent(1L, 7L, "EXECUTE", "execute-key", "sha256:new", 99L)).thenReturn(0);
        AgentIdempotency existing = new AgentIdempotency();
        existing.setRequestHash("sha256:old");
        existing.setTaskId(42L);
        when(mapper.selectDomainKey(1L, 7L, "EXECUTE", "execute-key")).thenReturn(existing);

        assertThatThrownBy(() -> service.bind(
                1L, 7L, IdempotencyOperation.EXECUTE, "execute-key", "sha256:new", 99L
        )).isInstanceOf(IdempotencyConflictException.class);
    }
}
