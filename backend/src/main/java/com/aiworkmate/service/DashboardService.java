package com.aiworkmate.service;

import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.dto.DashboardExportRequest;
import com.aiworkmate.dto.DashboardExportResponse;

public interface DashboardService {
    DashboardOverviewResponse overview(Long userId, int days);

    DashboardExportResponse export(Long userId, DashboardExportRequest request);
}
