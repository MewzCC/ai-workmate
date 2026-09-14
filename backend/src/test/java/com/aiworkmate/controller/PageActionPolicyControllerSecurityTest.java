package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.PageActionOverviewResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.PageActionPolicyService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PageActionPolicyController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class PageActionPolicyControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean PageActionPolicyService service;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService accessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(accessService.resolveActiveUser(42L)).thenReturn(new ResolvedUserAccess(
                42L, "system", 9L, "SYSTEM_ADMIN", List.of("SYSTEM_ADMIN"),
                List.of("route:page-actions", "page-action:manage"), List.of("TENANT"), 1L));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/admin/page-actions")).andExpect(status().isUnauthorized());
    }

    @Test
    void overviewUsesAuthenticatedIdentity() throws Exception {
        when(service.overview(42L)).thenReturn(new PageActionOverviewResponse(List.of(), 0, 0, 0, true));
        mvc.perform(get("/api/admin/page-actions").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        verify(service).overview(42L);
    }

    @Test
    void updateRequiresReasonAndVersion() throws Exception {
        mvc.perform(put("/api/admin/page-actions/dashboard/todo.query")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest());
        verify(service, never()).update(any(), any(), any(), any());
    }
}
