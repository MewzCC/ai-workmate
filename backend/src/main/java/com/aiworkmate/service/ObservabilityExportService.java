package com.aiworkmate.service;

import com.aiworkmate.dto.DashboardExportResponse;
import com.aiworkmate.dto.ObservabilityExportRequest;

public interface ObservabilityExportService {
    DashboardExportResponse export(Long userId, ObservabilityExportRequest request);
}
