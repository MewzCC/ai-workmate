package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.ContractOptionsResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.ContractService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContractController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class ContractControllerSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ContractService contractService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(new ResolvedUserAccess(
                42L, "finance@example.com", 9L, "FINANCE_ADMIN", List.of("FINANCE_ADMIN"),
                List.of("route:contracts", "contract:manage"), List.of("TENANT"), 2L));
    }

    @Test
    void endpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/contracts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void optionsUsesAuthenticatedUserIdentity() throws Exception {
        when(contractService.options(42L)).thenReturn(new ContractOptionsResponse(List.of(), List.of()));

        mvc.perform(get("/api/contracts/options").header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        verify(contractService).options(42L);
    }

    @Test
    void malformedPaymentPayloadIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/contracts/8/payments")
                        .header("Authorization", "Bearer valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("amount", 0, "version", 1))))
                .andExpect(status().isBadRequest());
        verify(contractService, never()).recordPayment(anyLong(), anyLong(), any());
    }
}
