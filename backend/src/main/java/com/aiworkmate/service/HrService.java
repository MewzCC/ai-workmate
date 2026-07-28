package com.aiworkmate.service;

import com.aiworkmate.dto.OrganizationOverviewResponse;

public interface HrService {

    OrganizationOverviewResponse overview(Long tenantId);
}
