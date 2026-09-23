package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.PlatformObservabilityService;
import com.aiworkmate.service.ObservabilityPreferenceService;
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
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PlatformObservabilityController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class PlatformObservabilityControllerSecurityTest {
    private static final String VALID_PREFERENCES = """
            {"charts":[
              {"id":"volume","mode":"line","content":["HUMAN"],"size":"normal"},
              {"id":"risk","mode":"mixed","content":["failed"],"size":"normal"},
              {"id":"source","mode":"donut","content":["HUMAN"],"size":"normal"},
              {"id":"error","mode":"bar","content":[],"size":"normal"}
            ]}
            """;
    @Autowired MockMvc mvc;
    @MockBean PlatformObservabilityService service;
    @MockBean ObservabilityPreferenceService preferenceService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService accessService;

    @BeforeEach void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
    }

    @Test void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/platform-observability/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test void deniesMissingReadPermission() throws Exception {
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of("route:platform-observability")));
        mvc.perform(get("/api/admin/platform-observability/overview")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        verify(service, never()).overview(42L, "7d");
    }

    @Test void delegatesOnlyTheAuthenticatedUser() throws Exception {
        mvc.perform(get("/api/admin/platform-observability/overview")
                        .header("Authorization", "Bearer valid")
                        .param("range", "24h"))
                .andExpect(status().isOk());
        verify(service).overview(42L, "24h");
    }

    @Test void protectsAndDelegatesTheAlternateTimeline() throws Exception {
        mvc.perform(get("/api/admin/platform-observability/timeline")
                        .param("from", "2026-09-16T00:00:00")
                        .param("to", "2026-09-23T00:00:00")
                        .param("interval", "hour"))
                .andExpect(status().isUnauthorized());
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of("route:platform-observability")));
        mvc.perform(get("/api/admin/platform-observability/timeline")
                        .header("Authorization", "Bearer valid")
                        .param("from", "2026-09-16T00:00:00")
                        .param("to", "2026-09-23T00:00:00")
                        .param("interval", "hour"))
                .andExpect(status().isForbidden());
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of(
                "route:platform-observability", "runtime-log:read")));
        mvc.perform(get("/api/admin/platform-observability/timeline")
                        .header("Authorization", "Bearer valid")
                        .param("from", "2026-09-16T00:00:00")
                        .param("to", "2026-09-23T00:00:00")
                        .param("interval", "hour"))
                .andExpect(status().isOk());
        verify(service).timeline(42L, LocalDateTime.parse("2026-09-16T00:00:00"),
                LocalDateTime.parse("2026-09-23T00:00:00"), "hour");
    }

    @Test void protectsPreferenceReadAndWriteWithTheSameLivePermissions() throws Exception {
        mvc.perform(get("/api/admin/platform-observability/preferences"))
                .andExpect(status().isUnauthorized());
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of("route:platform-observability")));
        mvc.perform(get("/api/admin/platform-observability/preferences")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/platform-observability/preferences")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_PREFERENCES))
                .andExpect(status().isForbidden());
        verify(preferenceService, never()).preferences(42L);
    }

    @Test void rejectsMalformedPreferenceBeforeService() throws Exception {
        mvc.perform(put("/api/admin/platform-observability/preferences")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"charts\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(42L, "system", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 1L);
    }
}
