package com.aiworkmate.dto;

public record AccessRouteResponse(
        String routeKey,
        String parentKey,
        String name,
        String path,
        String icon,
        String routeType,
        String componentKey,
        String permissionCode,
        int sortOrder,
        boolean enabled
) {
}
