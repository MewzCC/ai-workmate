package com.aiworkmate.service.impl;

import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AgentTaskDetailResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AgentTaskCommandService;
import com.aiworkmate.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentTaskCommandServiceImpl implements AgentTaskCommandService {
    private final UserAccessService accessService;
    private final AgentTaskApiService taskService;

    @Override
    public AgentTaskDetailResponse cancel(Long userId, String taskId) {
        var actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains("agent:task:read")) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        var authenticated = new AuthenticatedUser(actor.userId(), actor.username(), actor.tenantId(), actor.role(),
                actor.roles(), actor.permissions(), actor.dataScopes(), actor.permissionVersion());
        return taskService.cancel(authenticated, taskId);
    }
}
