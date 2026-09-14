package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.DashboardService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.UserSettingsService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserSettingsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class UserSettingsControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean UserSettingsService userSettingsService;
    @MockBean DashboardService dashboardService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(new ResolvedUserAccess(
                42L, "employee", 9L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("dashboard:read"), List.of("SELF"), 1L));
    }

    @Test
    void dashboardPreferencesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/settings/dashboard")).andExpect(status().isUnauthorized());
        verify(dashboardService, never()).preferences(42L);
    }

    @Test
    void dashboardPreferencesUseAuthenticatedIdentity() throws Exception {
        mvc.perform(get("/api/settings/dashboard").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        verify(dashboardService).preferences(42L);

        mvc.perform(put("/api/settings/dashboard").header("Authorization", "Bearer valid")
                        .contentType(APPLICATION_JSON)
                        .content("{\"metricCodes\":[\"UNREAD_MESSAGES\",\"PENDING_TODOS\"]}"))
                .andExpect(status().isOk());
        verify(dashboardService).updatePreferences(eq(42L), any());
    }

    @Test
    void rejectsEmptyDashboardMetricSelection() throws Exception {
        mvc.perform(put("/api/settings/dashboard").header("Authorization", "Bearer valid")
                        .contentType(APPLICATION_JSON).content("{\"metricCodes\":[]}"))
                .andExpect(status().isBadRequest());
        verify(dashboardService, never()).updatePreferences(eq(42L), any());
    }
}
