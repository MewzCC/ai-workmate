package com.aiworkmate.service;

import com.aiworkmate.agent.task.AgentTask;
import com.aiworkmate.agent.task.AgentTaskMapper;
import com.aiworkmate.common.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PageUiTaskSnapshotServiceTest {
    private final AgentTaskMapper tasks = mock(AgentTaskMapper.class);
    private final PageUiTaskSnapshotService service = new PageUiTaskSnapshotService(tasks, new ObjectMapper());

    private AgentTask owned(String context) {
        AgentTask task = new AgentTask(); task.setId(3L); task.setTenantId(1L); task.setUserId(2L);
        task.setStatus("RUNNING"); task.setPageId("dictionary"); task.setPageContext(context);
        when(tasks.selectOwnedRunningSnapshot(1L, 2L, 3L)).thenReturn(task); return task;
    }
    @Test void returnsOnlyTheOwnedFrozenFormIdentity() {
        owned("{\"uiFormSession\":\"form_session_123456\",\"uiRecordId\":5,\"uiRecordVersion\":2,\"name\":\"ignored\"}");
        assertThat(service.runningSnapshot(1L, 2L, 3L)).isEqualTo(
                new PageUiTaskSnapshotService.Snapshot("dictionary", "form_session_123456", 5L, 2));
        verify(tasks).selectOwnedRunningSnapshot(1L, 2L, 3L);
        verify(tasks, never()).selectById(anyLong());
    }
    @Test void rejectsForeignOrNonRunningTasks() {
        owned("{}");
        assertThatThrownBy(() -> service.runningSnapshot(9L, 2L, 3L)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.runningSnapshot(1L, 9L, 3L)).isInstanceOf(BusinessException.class);
        when(tasks.selectOwnedRunningSnapshot(1L, 2L, 3L)).thenReturn(null);
        assertThatThrownBy(() -> service.runningSnapshot(1L, 2L, 3L)).isInstanceOf(BusinessException.class);
    }
    @Test void failsClosedForMalformedOrIncompleteFormBinding() {
        for (String context : new String[]{"{", "[]", "null", "{} {}", "{\"uiFormSession\":\"short\"}",
                "{\"uiRecordId\":5,\"uiRecordVersion\":2}",
                "{\"uiFormSession\":\"form_session_123456\",\"uiFormSession\":\"other_session_123456\"}",
                "{\"uiFormSession\":\"form_session_123456\",\"uiRecordId\":5}",
                "{\"uiFormSession\":\"form_session_123456\",\"uiRecordId\":5,\"uiRecordVersion\":-1}"}) {
            owned(context);
            assertThatThrownBy(() -> service.runningSnapshot(1L, 2L, 3L)).isInstanceOf(BusinessException.class);
        }
    }
    @Test void refusesMissingIdentityBeforeQuerying() {
        assertThatThrownBy(() -> service.runningSnapshot(null, 2L, 3L)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(tasks);
    }
}
