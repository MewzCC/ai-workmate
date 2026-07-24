package com.aiworkmate.dto;

import java.util.List;

public record AccessControlOverviewResponse(
        List<AccessUserResponse> users,
        List<AccessRoleResponse> roles,
        List<AccessPermissionResponse> permissions,
        List<AccessRouteResponse> routes
) {
}
