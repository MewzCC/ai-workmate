package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AiOperationPermissionOverviewResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiOperationPermissionQueryService;
import com.aiworkmate.service.AiOperationPermissionService;
import com.aiworkmate.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AiOperationPermissionQueryServiceImpl implements AiOperationPermissionQueryService {
    private final UserAccessService accessService;
    private final AiOperationPermissionService permissionService;

    @Override
    @Transactional(readOnly = true)
    public AiOperationPermissionOverviewResponse overview(Long userId) {
        var actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains("agent-permission:manage")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return permissionService.overview(new AuthenticatedUser(actor.userId(), actor.username(), actor.tenantId(),
                actor.role(), actor.roles(), actor.permissions(), actor.dataScopes(), actor.permissionVersion()));
    }
}
