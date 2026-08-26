package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTaskEventServiceTest {
    private final AgentTaskMapper taskMapper = mock(AgentTaskMapper.class);
    private final AgentTaskEventMapper eventMapper = mock(AgentTaskEventMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentSseEmitterFactory emitterFactory = mock(AgentSseEmitterFactory.class);
    private final AgentTaskEventService service = new AgentTaskEventService(
            taskMapper, eventMapper, objectMapper, emitterFactory);

    @Test
    void replayQueryIsAlwaysScopedByOwnerAndLastEventId() {
        AgentTask task = new AgentTask();
        task.setId(15L);
        task.setStatus("RUNNING");
        when(taskMapper.selectOwned(9L, 7L, "task-owned")).thenReturn(task);
        when(eventMapper.selectOwnedEvents(9L, 7L, "task-owned", 41L, 1000)).thenReturn(List.of());
        when(emitterFactory.create(any(Long.class))).thenReturn(new RecordingEmitter());

        service.open(9L, 7L, "task-owned", 41L);

        verify(eventMapper).selectOwnedEvents(9L, 7L, "task-owned", 41L, 1000);
    }

    @Test
    void crossOwnerSubscriptionFailsWithoutReadingEvents() {
        when(taskMapper.selectOwned(9L, 8L, "task-owned")).thenReturn(null);

        assertThatThrownBy(() -> service.open(9L, 8L, "task-owned", 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getErrorCode()));
        verify(eventMapper, never()).selectOwnedEvents(any(), any(), any(), any(), any(Integer.class));
    }

    @Test
    void publishedEventContainsOnlyExplicitPayloadAndPersistsBeforeBroadcast() {
        when(eventMapper.insertEvent(any(AgentTaskEvent.class))).thenAnswer(invocation -> {
            AgentTaskEvent event = invocation.getArgument(0);
            event.setId(51L);
            return event;
        });
        var payload = objectMapper.createObjectNode().put("status", "RUNNING");

        AgentTaskEvent event = service.publish(15L, "snapshot", payload, "trace-event-test");

        assertThat(event.getId()).isEqualTo(51L);
        ArgumentCaptor<AgentTaskEvent> persisted = ArgumentCaptor.forClass(AgentTaskEvent.class);
        verify(eventMapper).insertEvent(persisted.capture());
        assertThat(persisted.getValue().getPayload()).isEqualTo("{\"status\":\"RUNNING\"}");
    }

    @Test
    void replayDeduplicatesIdsHeartbeatIsBoundedAndTerminalClosesSubscription() {
        AgentTask task = new AgentTask();
        task.setId(15L);
        task.setStatus("RUNNING");
        AgentTaskEvent replay = event(42L, "snapshot", "{\"status\":\"RUNNING\"}");
        AgentTaskEvent duplicate = event(42L, "snapshot", "{\"status\":\"RUNNING\"}");
        RecordingEmitter emitter = new RecordingEmitter();
        when(emitterFactory.create(any(Long.class))).thenReturn(emitter);
        when(taskMapper.selectOwned(9L, 7L, "task-owned")).thenReturn(task);
        when(eventMapper.selectOwnedEvents(9L, 7L, "task-owned", 41L, 1000))
                .thenReturn(List.of(replay, duplicate));
        when(eventMapper.insertEvent(any())).thenAnswer(invocation -> {
            AgentTaskEvent value = invocation.getArgument(0);
            value.setId(43L);
            return value;
        });

        service.open(9L, 7L, "task-owned", 41L);
        assertThat(emitter.sent).hasValue(1);
        service.heartbeat();
        assertThat(emitter.sent).hasValue(2);
        service.publish(15L, "task-completed", objectMapper.createObjectNode().put("status", "SUCCEEDED"), "trace");

        assertThat(emitter.sent).hasValue(3);
        assertThat(emitter.completed).hasValue(1);
        service.heartbeat();
        assertThat(emitter.sent).hasValue(3);
    }

    private AgentTaskEvent event(long id, String type, String payload) {
        AgentTaskEvent event = new AgentTaskEvent();
        event.setId(id);
        event.setTaskId(15L);
        event.setEventType(type);
        event.setPayload(payload);
        return event;
    }

    private static final class RecordingEmitter extends SseEmitter {
        private final AtomicInteger sent = new AtomicInteger();
        private final AtomicInteger completed = new AtomicInteger();

        private RecordingEmitter() {
            super(120_000L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sent.incrementAndGet();
        }

        @Override
        public synchronized void complete() {
            completed.incrementAndGet();
        }
    }
}
