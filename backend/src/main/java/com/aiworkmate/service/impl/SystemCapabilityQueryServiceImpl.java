package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.SystemCapabilitiesResponse;
import com.aiworkmate.service.SystemCapabilityQueryService;
import com.aiworkmate.service.SystemCapabilityService;
import com.aiworkmate.service.UserAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemCapabilityQueryServiceImpl implements SystemCapabilityQueryService {
    private final UserAccessService accessService;
    private final SystemCapabilityService capabilityService;

    @Override
    @Transactional(readOnly = true)
    public SystemCapabilitiesResponse inspect(Long userId) {
        var actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains("access:manage")) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return capabilityService.inspect();
    }
}
