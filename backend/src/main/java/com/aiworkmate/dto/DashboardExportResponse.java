package com.aiworkmate.dto;

import java.time.OffsetDateTime;

public record DashboardExportResponse(
        String filename,
        String contentType,
        String content,
        int rowCount,
        OffsetDateTime generatedAt
) {}
