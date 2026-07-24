package com.aiworkmate.service.impl;

import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.NavigationRouteResponse;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.service.NavigationService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NavigationServiceImpl implements NavigationService {

    private final AccessControlMapper accessControlMapper;
    private final UserAccessService userAccessService;

    @Override
    @Transactional(readOnly = true)
    public List<NavigationRouteResponse> navigation(Long userId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null) {
            return List.of();
        }
        Set<String> permissions = Set.copyOf(access.permissions());
        List<AccessRouteResponse> visible = accessControlMapper.selectRoutes().stream()
                .filter(AccessRouteResponse::enabled)
                .filter(route -> !"PAGE".equals(route.routeType())
                        || permissions.contains(route.permissionCode()))
                .toList();
        return childrenOf(null, visible);
    }

    private List<NavigationRouteResponse> childrenOf(String parentKey, List<AccessRouteResponse> routes) {
        return routes.stream()
                .filter(route -> java.util.Objects.equals(parentKey, route.parentKey()))
                .sorted(Comparator.comparingInt(AccessRouteResponse::sortOrder)
                        .thenComparing(AccessRouteResponse::routeKey))
                .map(route -> toNavigationRoute(route, routes))
                .filter(route -> "PAGE".equals(route.routeType()) || !route.children().isEmpty())
                .toList();
    }

    private NavigationRouteResponse toNavigationRoute(AccessRouteResponse route,
                                                       List<AccessRouteResponse> routes) {
        return new NavigationRouteResponse(
                route.routeKey(),
                route.parentKey(),
                route.name(),
                route.path(),
                route.icon(),
                route.routeType(),
                route.componentKey(),
                route.permissionCode(),
                route.sortOrder(),
                childrenOf(route.routeKey(), routes)
        );
    }
}
