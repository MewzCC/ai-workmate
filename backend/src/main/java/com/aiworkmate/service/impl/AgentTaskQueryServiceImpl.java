package com.aiworkmate.service.impl;

import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AgentTaskSummaryResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AgentTaskQueryService;
import com.aiworkmate.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentTaskQueryServiceImpl implements AgentTaskQueryService {
    private final UserAccessService accessService;
    private final AgentTaskApiService taskService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AgentTaskSummaryResponse> mine(Long userId, String status, LocalDateTime from,
                                                       LocalDateTime to, int page, int size) {
        var actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains("agent:task:read")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        var authenticated = new AuthenticatedUser(actor.userId(), actor.username(), actor.tenantId(), actor.role(),
                actor.roles(), actor.permissions(), actor.dataScopes(), actor.permissionVersion());
        return taskService.list(authenticated, status, from, to, page, size);
    }
}
