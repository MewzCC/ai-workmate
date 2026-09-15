package com.aiworkmate.service.impl;

import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentTaskQueryServiceImplTest {
    private final UserAccessService accessService = mock(UserAccessService.class);
    private final AgentTaskApiService taskService = mock(AgentTaskApiService.class);
    private final AgentTaskQueryServiceImpl service = new AgentTaskQueryServiceImpl(accessService, taskService);

    @Test
    void rejectsMissingOrUnauthorizedActorBeforeTaskLookup() {
        when(accessService.resolveActiveUser(1L)).thenReturn(null);
        when(accessService.resolveActiveUser(2L)).thenReturn(actor(List.of()));
        assertThatThrownBy(() -> service.mine(1L, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REQUIRED.getErrorCode());
        assertThatThrownBy(() -> service.mine(2L, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode());
        verifyNoInteractions(taskService);
    }

    @Test
    void delegatesWithLiveIdentitySnapshot() {
        when(accessService.resolveActiveUser(3L)).thenReturn(actor(List.of("agent:task:read")));
        service.mine(3L, "RUNNING", null, null, 1, 20);
        verify(taskService).list(argThat(user -> user.userId() == 3L && user.tenantId() == 9L),
                eq("RUNNING"), isNull(), isNull(), eq(1), eq(20));
    }

    private ResolvedUserAccess actor(List<String> permissions) {
        return new ResolvedUserAccess(3L, "employee", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), permissions, List.of("SELF"), 7L);
    }
}
