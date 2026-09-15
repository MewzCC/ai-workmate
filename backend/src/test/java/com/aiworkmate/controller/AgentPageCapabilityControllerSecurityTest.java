package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.PageCapabilityResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.PageCapabilityService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentPageCapabilityController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class AgentPageCapabilityControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean PageCapabilityService pageCapabilityService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(new ResolvedUserAccess(
                42L, "alice", 9L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("route:todo", "todo:read"), List.of("SELF"), 3L));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/ai/pages/todo/capabilities"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(pageCapabilityService);
    }

    @Test
    void passesOnlyAuthenticatedIdentityAndPathPage() throws Exception {
        when(pageCapabilityService.resolve(42L, "todo")).thenReturn(new PageCapabilityResponse(
                "todo", "TODO_LIST", 1, List.of("ui.navigate"), "ASSIGNED_TO_SELF",
                List.of("SELF"), List.of(), PageCapabilityResponse.UnavailableReason.NO_AVAILABLE_TOOLS));

        mvc.perform(get("/api/ai/pages/todo/capabilities")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pageId").value("todo"))
                .andExpect(jsonPath("$.data.componentKey").value("TODO_LIST"))
                .andExpect(jsonPath("$.data.unavailableReason").value("NO_AVAILABLE_TOOLS"));

        verify(pageCapabilityService).resolve(42L, "todo");
    }
}
