package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.DashboardService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class DashboardControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean DashboardService dashboardService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access(List.of("dashboard:read")));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/dashboard/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresDashboardReadPermission() throws Exception {
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access(List.of("route:dashboard")));
        mvc.perform(get("/api/dashboard/overview").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        verify(dashboardService, never()).overview(42L, 7);
    }

    @Test
    void passesAuthenticatedIdentityAndValidatedRange() throws Exception {
        when(dashboardService.overview(42L, 14)).thenReturn(new DashboardOverviewResponse(
                OffsetDateTime.now(), 14, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null));
        mvc.perform(get("/api/dashboard/overview").header("Authorization", "Bearer valid").param("days", "14"))
                .andExpect(status().isOk());
        verify(dashboardService).overview(42L, 14);
    }

    @Test
    void rejectsOutOfRangeDays() throws Exception {
        mvc.perform(get("/api/dashboard/overview").header("Authorization", "Bearer valid").param("days", "31"))
                .andExpect(status().isBadRequest());
        verify(dashboardService, never()).overview(42L, 31);
    }

    @Test
    void exportRequiresLiveDataExportPermission() throws Exception {
        mvc.perform(post("/api/dashboard/export").header("Authorization", "Bearer valid")
                        .contentType(APPLICATION_JSON).content("{\"from\":\"2026-09-06\",\"to\":\"2026-09-12\"}"))
                .andExpect(status().isForbidden());
        verify(dashboardService, never()).export(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void exportPassesValidatedRangeForAuthorizedUser() throws Exception {
        when(userAccessService.resolveActiveUser(42L)).thenReturn(access(List.of("dashboard:read", "data:export")));
        mvc.perform(post("/api/dashboard/export").header("Authorization", "Bearer valid")
                        .contentType(APPLICATION_JSON).content("{\"from\":\"2026-09-06\",\"to\":\"2026-09-12\"}"))
                .andExpect(status().isOk());
        verify(dashboardService).export(org.mockito.ArgumentMatchers.eq(42L), org.mockito.ArgumentMatchers.any());
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(42L, "employee", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), permissions, List.of("SELF"), 1L);
    }
}
