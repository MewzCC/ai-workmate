package com.aiworkmate.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DataPermissionPolicyResponse(
        Long id, String name, String description, String scopeType,
        List<Long> departmentIds, boolean enabled, long version, LocalDateTime updatedAt) {
}
