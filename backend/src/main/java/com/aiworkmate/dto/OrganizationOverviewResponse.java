package com.aiworkmate.dto;

import java.util.List;

public record OrganizationOverviewResponse(
        List<DepartmentResponse> departments,
        List<PositionResponse> positions,
        List<OrganizationMemberResponse> members,
        boolean canManage
) {
}
