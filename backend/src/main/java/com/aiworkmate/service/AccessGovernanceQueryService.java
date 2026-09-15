package com.aiworkmate.service;

import com.aiworkmate.dto.AccessControlOverviewResponse;

/** Authenticated read facade for access governance consumers outside MVC pre-authorization. */
public interface AccessGovernanceQueryService {
    AccessControlOverviewResponse overview(Long userId);
}
