package com.aiworkmate.agent.retention;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.observability.AgentOperationalObserver;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

class AgentRetentionCleanerTest {
    private final AgentRuntimeProperties properties = new AgentRuntimeProperties();
    private final AgentRetentionMapper mapper = mock(AgentRetentionMapper.class);
    private final AgentOperationalObserver observer = mock(AgentOperationalObserver.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-26T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void cleanupIsTenantScopedBoundedAndDeletesDetailsBeforeTerminalTasks() {
        properties.setRetentionBatchSize(25);
        when(mapper.selectEventTenants(any(), eq(100))).thenReturn(List.of(7L, 8L));
        when(mapper.selectInvocationTenants(any(), eq(100))).thenReturn(List.of(7L));
        when(mapper.selectTaskTenants(any(), eq(100))).thenReturn(List.of(8L));
        when(mapper.deleteEventBatch(anyLong(), any(), eq(25))).thenReturn(4, 3);
        when(mapper.deleteInvocationBatch(eq(7L), any(), eq(25))).thenReturn(2);
        when(mapper.deleteTaskBatch(eq(8L), any(), eq(25))).thenReturn(1);

        new AgentRetentionCleaner(properties, mapper, observer, clock).clean();

        var order = inOrder(mapper);
        order.verify(mapper).selectEventTenants(any(), eq(100));
        order.verify(mapper).deleteEventBatch(eq(7L), any(), eq(25));
        order.verify(mapper).deleteEventBatch(eq(8L), any(), eq(25));
        order.verify(mapper).selectInvocationTenants(any(), eq(100));
        order.verify(mapper).deleteInvocationBatch(eq(7L), any(), eq(25));
        order.verify(mapper).selectTaskTenants(any(), eq(100));
        order.verify(mapper).deleteTaskBatch(eq(8L), any(), eq(25));
        verify(observer).retentionCompleted(7, 2, 1, 4);
    }

    @Test
    void disabledCleanupNeverReadsOrDeletesPersistence() {
        properties.setRetentionCleanupEnabled(false);

        new AgentRetentionCleaner(properties, mapper, observer, clock).clean();

        verifyNoInteractions(mapper, observer);
    }

    @Test
    void persistenceFailureIsFailClosedAndReportedWithoutPayload() {
        when(mapper.selectEventTenants(any(), eq(100))).thenThrow(new IllegalStateException("database unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new AgentRetentionCleaner(properties, mapper, observer, clock).clean())
                .isInstanceOf(IllegalStateException.class);

        verify(observer).retentionFailed(0);
        verify(mapper, never()).deleteTaskBatch(anyLong(), any(), anyInt());
    }
}
