package com.aiworkmate.agent.gateway;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseGatewayAuditWriterTest {
    private final AgentToolInvocationMapper mapper = mock(AgentToolInvocationMapper.class);
    private final DatabaseGatewayAuditWriter writer = new DatabaseGatewayAuditWriter(mapper);

    @Test
    void shouldPersistOnlyHashedArgumentsAndLowSensitivityMetadata() {
        when(mapper.insert(any(AgentToolInvocation.class))).thenReturn(1);
        GatewayExecutionSnapshot snapshot = new GatewayExecutionSnapshot();
        snapshot.setTenantId(1L);
        snapshot.setUserId(7L);
        snapshot.setTaskId(5L);
        snapshot.setStepId(10L);
        snapshot.setStepAttempt(0);
        snapshot.setToolCode("todo.query");
        snapshot.setToolVersion("1.0.0");
        snapshot.setArgsHash("sha256:args");
        snapshot.setArguments("{\"token\":\"must-not-persist\"}");
        snapshot.setTraceId("trace-1");

        writer.record(snapshot, GatewayDecision.ALLOW, "POLICY_ALLOWED", false);

        ArgumentCaptor<AgentToolInvocation> captor = ArgumentCaptor.forClass(AgentToolInvocation.class);
        verify(mapper).insert(captor.capture());
        AgentToolInvocation saved = captor.getValue();
        assertThat(saved.getArgsHash()).isEqualTo("sha256:args");
        assertThat(saved.getArgsSummary()).isNull();
        assertThat(saved.toString()).doesNotContain("must-not-persist");
        assertThat(saved.getDecisionId()).hasSize(36);
    }
}
