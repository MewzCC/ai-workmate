package com.aiworkmate.dto;

import java.util.List;

public record DataPermissionPreviewResponse(
        Long userId, String source, List<String> scopeTypes,
        List<Long> departmentIds, List<Long> visibleUserIds) {
}
