package com.aiworkmate.service;

import com.aiworkmate.dto.AiOperationPermissionOverviewResponse;

/** Authenticated read facade for Agent permission governance. */
public interface AiOperationPermissionQueryService {
    AiOperationPermissionOverviewResponse overview(Long userId);
}
