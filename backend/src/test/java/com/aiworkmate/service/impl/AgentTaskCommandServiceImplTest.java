package com.aiworkmate.service.impl;

import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AgentTaskDetailResponse;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class AgentTaskCommandServiceImplTest {
    private final UserAccessService accessService = mock(UserAccessService.class);
    private final AgentTaskApiService taskService = mock(AgentTaskApiService.class);
    private final AgentTaskCommandServiceImpl service = new AgentTaskCommandServiceImpl(accessService, taskService);

    @Test
    void rejectsMissingOrUnauthorizedActorBeforeTaskMutation() {
        when(accessService.resolveActiveUser(1L)).thenReturn(null);
        when(accessService.resolveActiveUser(2L)).thenReturn(actor(2L, List.of()));

        assertThatThrownBy(() -> service.cancel(1L, "agt-target"))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.AUTH_REQUIRED.getErrorCode());
        assertThatThrownBy(() -> service.cancel(2L, "agt-target"))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode());
        verifyNoInteractions(taskService);
    }

    @Test
    void delegatesWithFreshIdentityAndLeavesOwnershipAndStateChecksToTaskService() {
        var response = mock(AgentTaskDetailResponse.class);
        when(accessService.resolveActiveUser(3L)).thenReturn(actor(3L, List.of("agent:task:read")));
        when(taskService.cancel(argThat(user -> user.userId() == 3L && user.tenantId() == 9L),
                eq("agt-target"))).thenReturn(response);

        assertThat(service.cancel(3L, "agt-target")).isSameAs(response);
        verify(taskService).cancel(argThat(user -> user.userId() == 3L && user.tenantId() == 9L),
                eq("agt-target"));
    }

    private ResolvedUserAccess actor(long userId, List<String> permissions) {
        return new ResolvedUserAccess(userId, "employee", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), permissions, List.of("SELF"), 7L);
    }
}
