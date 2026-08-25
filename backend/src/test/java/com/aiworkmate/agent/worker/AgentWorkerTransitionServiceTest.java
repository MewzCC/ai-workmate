package com.aiworkmate.agent.worker;

import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskEventService;
import com.aiworkmate.agent.task.AgentTaskStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentWorkerTransitionServiceTest {
    private final AgentWorkerMapper mapper = mock(AgentWorkerMapper.class);
    private final AgentTaskEventService events = mock(AgentTaskEventService.class);
    private final AgentWorkerTransitionService service = new AgentWorkerTransitionService(
            mapper, events, new ObjectMapper());

    @Test
    void persistsStepStartedEventWithStateTransition() {
        AgentTask task = task();
        AgentTaskStep step = step();
        when(mapper.startNextStep(10L, "worker", "lease-hash", 0, LocalDateTime.MAX)).thenReturn(step);

        assertThat(service.startNextStep(task, "worker", "lease-hash", LocalDateTime.MAX)).isSameAs(step);

        verify(events).publish(eq(10L), eq("step-started"), any(), eq("trace-step"));
    }

    @Test
    void staleCompletionCannotPublishMisleadingEvent() {
        when(mapper.completeStep(20L, 0, "worker", "lease-hash", "{}" )).thenReturn(0);

        assertThat(service.completeStep(task(), step(), "worker", "lease-hash", "{}")).isFalse();

        verify(events, never()).publish(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void successfulCompletionPublishesRedactedProgressEvent() {
        when(mapper.completeStep(20L, 0, "worker", "lease-hash", "{}" )).thenReturn(1);

        assertThat(service.completeStep(task(), step(), "worker", "lease-hash", "{}")).isTrue();

        verify(events).publish(eq(10L), eq("step-completed"), any(), eq("trace-step"));
    }

    private AgentTask task() {
        AgentTask task = new AgentTask();
        task.setId(10L);
        task.setTaskNo("00000000-0000-4000-8000-000000000010");
        task.setAttemptCount(0);
        task.setTraceId("trace-task");
        return task;
    }

    private AgentTaskStep step() {
        AgentTaskStep step = new AgentTaskStep();
        step.setId(20L);
        step.setSequenceNo(1);
        step.setToolCode("todo.query");
        step.setTraceId("trace-step");
        return step;
    }
}
