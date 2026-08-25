package com.aiworkmate.agent.task;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
    private final AgentTaskEventService service = new AgentTaskEventService(taskMapper, eventMapper, objectMapper);

    @Test
    void replayQueryIsAlwaysScopedByOwnerAndLastEventId() {
        AgentTask task = new AgentTask();
        task.setId(15L);
        task.setStatus("RUNNING");
        when(taskMapper.selectOwned(9L, 7L, "task-owned")).thenReturn(task);
        when(eventMapper.selectOwnedEvents(9L, 7L, "task-owned", 41L, 1000)).thenReturn(List.of());

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
}
