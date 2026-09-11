package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.RuntimeLogPageResponse;
import com.aiworkmate.dto.RuntimeLogStatsResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.RuntimeLogService;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RuntimeLogController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class RuntimeLogControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean RuntimeLogService service;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService accessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(accessService.resolveActiveUser(42L)).thenReturn(access(
                List.of("route:runtime-logs", "runtime-log:read")));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/runtime-logs")).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresDedicatedRuntimeLogPermission() throws Exception {
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of("route:runtime-logs")));

        mvc.perform(get("/api/admin/runtime-logs").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());

        verify(service, never()).query(any(), any(), any(), any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void queryUsesAuthenticatedIdentity() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        when(service.query(42L, "AGENT", null, null, null, null, 1, 20))
                .thenReturn(new RuntimeLogPageResponse(List.of(), 0, 1, 20,
                        now.minusDays(7), now, new RuntimeLogStatsResponse(0L, 0L, 0L, 0L, 0L)));

        mvc.perform(get("/api/admin/runtime-logs")
                        .header("Authorization", "Bearer valid")
                        .param("source", "AGENT"))
                .andExpect(status().isOk());

        verify(service).query(42L, "AGENT", null, null, null, null, 1, 20);
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(42L, "system", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 1L);
    }
}
