package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.AiOperationPermissionService;
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

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiOperationPermissionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class AiOperationPermissionControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockBean AiOperationPermissionService service;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("token")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("token")).thenReturn(10L);
    }

    @Test
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/ai-operation-permissions"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUserWithoutManagementPermission() throws Exception {
        resolve(List.of("agent:tool:todo.query"));
        mockMvc.perform(get("/api/admin/ai-operation-permissions")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void passesRealtimeAuthenticatedOperatorToService() throws Exception {
        resolve(List.of("agent-permission:manage"));
        mockMvc.perform(get("/api/admin/ai-operation-permissions")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk());
        verify(service).overview(anyOperator());
    }

    private void resolve(List<String> permissions) {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(new ResolvedUserAccess(
                10L, "admin", 9L, "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"),
                permissions, List.of("ALL"), 2L));
    }

    private static org.mockito.ArgumentMatcher<com.aiworkmate.security.AuthenticatedUser> anyOperatorMatcher() {
        return user -> user != null && user.userId().equals(10L) && user.tenantId().equals(9L);
    }

    private static com.aiworkmate.security.AuthenticatedUser anyOperator() {
        return org.mockito.ArgumentMatchers.argThat(anyOperatorMatcher());
    }
}
