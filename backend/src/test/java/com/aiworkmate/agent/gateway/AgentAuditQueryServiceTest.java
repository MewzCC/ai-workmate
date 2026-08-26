package com.aiworkmate.agent.gateway;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AgentAuditQueryServiceTest {
    private final AgentToolInvocationMapper mapper = mock(AgentToolInvocationMapper.class);
    private final AgentAuditQueryService service = new AgentAuditQueryService(mapper);

    @Test
    void queryRequiresFullOwnershipScopeAndCapsResultSize() {
        when(mapper.selectOwnedTaskAudit(7L, 9L, "opaque-task", 100)).thenReturn(List.of());

        assertThat(service.findOwnedTaskAudit(7L, 9L, "opaque-task", 500)).isEmpty();

        verify(mapper).selectOwnedTaskAudit(7L, 9L, "opaque-task", 100);
        assertThatThrownBy(() -> service.findOwnedTaskAudit(0, 9L, "opaque-task", 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findOwnedTaskAudit(7L, 9L, " ", 10))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
