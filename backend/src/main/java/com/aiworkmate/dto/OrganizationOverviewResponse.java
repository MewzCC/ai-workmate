package com.aiworkmate.dto;

import java.util.List;

public record OrganizationOverviewResponse(
        List<DepartmentResponse> departments,
        List<PositionResponse> positions,
        List<EmployeeSummary> employees
) {
    public record EmployeeSummary(
            Long id,
            String name,
            String email,
            String role,
            Integer status,
            Long departmentId,
            Long positionId,
            Long approverUserId,
            String approverName,
            String avatarUrl,
            String approverAvatarUrl
    ) {
    }
}
