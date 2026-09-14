package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.SandboxReplayDetailResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.SandboxReplayService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SandboxReplayController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class,
        GlobalExceptionHandler.class})
class SandboxReplayControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean SandboxReplayService service;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService accessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of(
                "route:sandbox-replay", "integration:replay:read", "integration:replay:execute")));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/integration/replays")).andExpect(status().isUnauthorized());
    }

    @Test
    void listRequiresDedicatedReadPermission() throws Exception {
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of("route:sandbox-replay")));

        mvc.perform(get("/api/integration/replays").header("Authorization", "Bearer valid"))
                .andExpect(status().isForbidden());

        verify(service, never()).list(any(), any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    void executeRequiresDedicatedExecutePermission() throws Exception {
        when(accessService.resolveActiveUser(42L)).thenReturn(access(List.of(
                "route:sandbox-replay", "integration:replay:read")));

        mvc.perform(post("/api/integration/replays")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceInvocationId":12,"reason":"验证历史接口行为",
                                 "idempotencyKey":"12345678-1234-1234-1234-123456789abc"}
                                """))
                .andExpect(status().isForbidden());

        verify(service, never()).execute(any(), any());
    }

    @Test
    void executeUsesAuthenticatedIdentity() throws Exception {
        when(service.execute(any(), any())).thenReturn(detail());

        mvc.perform(post("/api/integration/replays")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceInvocationId":12,"reason":"验证历史接口行为",
                                 "idempotencyKey":"12345678-1234-1234-1234-123456789abc"}
                                """))
                .andExpect(status().isOk());

        verify(service).execute(org.mockito.ArgumentMatchers.eq(42L), any());
    }

    private SandboxReplayDetailResponse detail() {
        return new SandboxReplayDetailResponse(1L, 12L, "CHECK", "校验", "GET", "/v1/check",
                "a".repeat(64), "SUCCESS", 200, "{}", "SUCCESS", 200, 12L,
                "{}", null, "MATCHED", "b".repeat(32), "验证历史接口行为", "system",
                LocalDateTime.now(), LocalDateTime.now());
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(42L, "system", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 1L);
    }
}
