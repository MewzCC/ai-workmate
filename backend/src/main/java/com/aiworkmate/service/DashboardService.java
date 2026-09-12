package com.aiworkmate.service;

import com.aiworkmate.dto.DashboardOverviewResponse;

public interface DashboardService {
    DashboardOverviewResponse overview(Long userId, int days);
}
