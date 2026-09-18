package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.service.AccessControlService;
import com.aiworkmate.service.AccessGovernanceQueryService;
import com.aiworkmate.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccessGovernanceQueryServiceImpl implements AccessGovernanceQueryService {
    private final UserAccessService accessService;
    private final AccessControlService accessControlService;

    @Override
    @Transactional(readOnly = true)
    public AccessControlOverviewResponse overview(Long userId) {
        var actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains("access:manage")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return accessControlService.overview(actor.tenantId());
    }
}
