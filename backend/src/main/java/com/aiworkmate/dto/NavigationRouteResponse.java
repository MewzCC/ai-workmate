package com.aiworkmate.dto;

import java.util.List;

public record NavigationRouteResponse(
        String routeKey,
        String parentKey,
        String name,
        String path,
        String icon,
        String routeType,
        String componentKey,
        String permissionCode,
        int sortOrder,
        List<NavigationRouteResponse> children
) {
}
