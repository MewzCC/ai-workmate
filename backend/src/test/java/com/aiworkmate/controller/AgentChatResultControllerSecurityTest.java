package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.impl.AgentChatResultService;
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

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgentChatResultController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class AgentChatResultControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean AgentChatResultService resultService;
    @MockBean JwtUtil jwtUtil;
    @MockBean UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);
        when(userAccessService.resolveActiveUser(42L)).thenReturn(new ResolvedUserAccess(
                42L, "employee", 9L, "EMPLOYEE", List.of("EMPLOYEE"),
                List.of("route:ai-workspace", "todo:read", "agent:tool:todo.query"), List.of("SELF"), 1L));
    }

    @Test
    void rejectsAnonymousAppend() throws Exception {
        mvc.perform(post("/api/conversations/3/agent-results/task-1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(resultService);
    }

    @Test
    void passesOnlyLiveAuthenticatedIdentityToService() throws Exception {
        mvc.perform(post("/api/conversations/3/agent-results/task-1")
                        .header("Authorization", "Bearer valid"))
                .andExpect(status().isOk());
        verify(resultService).appendResult(argThat(user -> user.userId() == 42L && user.tenantId() == 9L),
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq("task-1"));
    }
}
