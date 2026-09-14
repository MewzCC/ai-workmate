package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.SupplierService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class SupplierControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @MockBean SupplierService service;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("token")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("token")).thenReturn(10L);
        when(userAccessService.resolveActiveUser(10L)).thenReturn(new ResolvedUserAccess(
                10L, "finance@example.com", 9L, "FINANCE_ADMIN", List.of("FINANCE_ADMIN"),
                List.of("route:suppliers", "supplier:manage"), List.of("TENANT"), 2L));
    }

    @Test
    void rejectsAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/suppliers"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void passesAuthenticatedUserIdentityToListService() throws Exception {
        mockMvc.perform(get("/api/suppliers")
                        .header("Authorization", "Bearer token")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(service).list(eq(10L), eq(null), eq("ACTIVE"), eq(null), eq(1), eq(20));
    }

    @Test
    void rejectsInvalidSupplierBeforeServiceInvocation() throws Exception {
        mockMvc.perform(post("/api/suppliers")
                        .header("Authorization", "Bearer token")
                        .contentType("application/json")
                        .content("{\"code\":\"!\",\"name\":\"\",\"category\":\"UNKNOWN\",\"supplierLevel\":\"STANDARD\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
