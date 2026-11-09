package com.aiworkmate.service;

import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.dto.DashboardExportRequest;
import com.aiworkmate.dto.DashboardExportResponse;
import com.aiworkmate.dto.DashboardPreferenceRequest;
import com.aiworkmate.dto.DashboardPreferenceResponse;

public interface DashboardService {
    DashboardOverviewResponse overview(Long userId, int days);
    DashboardOverviewResponse overview(Long userId,Long expectedTenantId,int days);

    DashboardExportResponse export(Long userId, DashboardExportRequest request);
    DashboardExportResponse export(Long userId,Long expectedTenantId,DashboardExportRequest request);

    DashboardPreferenceResponse preferences(Long userId);

    DashboardPreferenceResponse updatePreferences(Long userId, DashboardPreferenceRequest request);
}
