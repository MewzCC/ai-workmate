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
        when(accessControlMapper.selectRoutes()).thenReturn(List.of(
                route("workspace", null, "GROUP", null, true, 1),
                route("dashboard", "workspace", "PAGE", "route:dashboard", true, 1),
                route("access-control", "workspace", "PAGE", "route:access-control", true, 2),
                route("disabled", "workspace", "PAGE", "route:dashboard", false, 3)
        ));

        var navigation = navigationService.navigation(7L);

        assertThat(navigation).extracting("routeKey").containsExactly("workspace");
        assertThat(navigation.get(0).children())
                .extracting("routeKey")
                .containsExactly("dashboard");
    }

    private AccessRouteResponse route(String key,
                                      String parent,
                                      String type,
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
                "PAGE".equals(type) ? "DASHBOARD" : null,
                permission,
                sort,
                enabled
        );
    }
}
