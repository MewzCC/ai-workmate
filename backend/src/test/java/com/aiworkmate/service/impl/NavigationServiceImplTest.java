package com.aiworkmate.service.impl;

import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavigationServiceImplTest {

    @Mock
    private AccessControlMapper accessControlMapper;

    @Mock
    private UserAccessService userAccessService;

    @InjectMocks
    private NavigationServiceImpl navigationService;

    @Test
    void shouldReturnOnlyPermittedPagesAndTheirAncestors() {
        when(userAccessService.resolveActiveUser(7L))
                .thenReturn(new ResolvedUserAccess(
                        7L, "employee@example.com", "EMPLOYEE", List.of("route:dashboard")));
        when(accessControlMapper.selectRoutesForTenant(1L)).thenReturn(List.of(
                route("workspace", null, "GROUP", null, null, true, 1),
                route("dashboard", "workspace", "PAGE", "DASHBOARD", "route:dashboard", true, 1),
                route("access-control", "workspace", "PAGE", "ACCESS_CONTROL", "route:access-control", true, 2),
                route("disabled", "workspace", "PAGE", "DASHBOARD", "route:dashboard", false, 3)
        ));

        var navigation = navigationService.navigation(7L);

        assertThat(navigation).extracting("routeKey").containsExactly("workspace");
        assertThat(navigation.get(0).children())
                .extracting("routeKey")
                .containsExactly("dashboard");
    }

    @Test
    void shouldFailClosedForEnabledPlaceholderAndMismatchedDashboardRoutes() {
        when(userAccessService.resolveActiveUser(7L))
                .thenReturn(new ResolvedUserAccess(
                        7L, "employee@example.com", "EMPLOYEE",
                        List.of("route:legacy", "route:fake-dashboard")));
        when(accessControlMapper.selectRoutesForTenant(1L)).thenReturn(List.of(
                route("workspace", null, "GROUP", null, null, true, 1),
                route("legacy", "workspace", "PAGE", "WORKBENCH_MODULE", "route:legacy", true, 1),
                route("fake-dashboard", "workspace", "PAGE", "DASHBOARD", "route:fake-dashboard", true, 2)
        ));

        assertThat(navigationService.navigation(7L)).isEmpty();
    }

    @Test
    void shouldReadRoutesOnlyFromResolvedUsersTenant() {
        when(userAccessService.resolveActiveUser(7L))
                .thenReturn(new ResolvedUserAccess(
                        7L, "employee@example.com", 99L, "EMPLOYEE", List.of("EMPLOYEE"),
                        List.of("route:dashboard"), List.of("SELF"), 8L));
        when(accessControlMapper.selectRoutesForTenant(99L)).thenReturn(List.of(
                route("workspace", null, "GROUP", null, null, true, 1),
                route("dashboard", "workspace", "PAGE", "DASHBOARD", "route:dashboard", true, 1)
        ));

        var navigation = navigationService.navigation(7L);

        assertThat(navigation).hasSize(1);
        assertThat(navigation.get(0).children()).extracting("routeKey").containsExactly("dashboard");
        verify(accessControlMapper).selectRoutesForTenant(99L);
    }

    private AccessRouteResponse route(String key,
                                      String parent,
                                      String type,
                                      String componentKey,
                                      String permission,
                                      boolean enabled,
                                      int sort) {
        return new AccessRouteResponse(
                key,
                parent,
                key,
                "PAGE".equals(type) ? "/oa/" + key : null,
                null,
                type,
                componentKey,
                permission,
                sort,
                enabled
        );
    }
}
