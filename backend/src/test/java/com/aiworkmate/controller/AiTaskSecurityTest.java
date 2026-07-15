package com.aiworkmate.controller;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.AiTaskService;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiTaskController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class AiTaskSecurityTest {

    private static final String TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiTaskService aiTaskService;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus(TOKEN)).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(1001L);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn("alice");
    }

    @Test
    void shouldRejectAnonymousPlan() throws Exception {
        mockMvc.perform(post("/api/ai/tasks/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"查看待办\",\"pageId\":\"dashboard\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"))
                .andExpect(header().exists(RequestTraceFilter.REQUEST_ID_HEADER));

        verifyNoInteractions(aiTaskService);
    }

    @Test
    void shouldRejectForgedToken() throws Exception {
        when(jwtUtil.validateTokenStatus("forged")).thenReturn(JwtValidationStatus.INVALID);

        mockMvc.perform(post("/api/ai/tasks/plan")
                        .header("Authorization", "Bearer forged")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"查看待办\",\"pageId\":\"dashboard\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        when(jwtUtil.validateTokenStatus("expired")).thenReturn(JwtValidationStatus.EXPIRED);

        mockMvc.perform(post("/api/ai/tasks/plan")
                        .header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"查看待办\",\"pageId\":\"dashboard\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void shouldRejectEmployeeExecution() throws Exception {
        when(jwtUtil.getRoleFromToken(TOKEN)).thenReturn("USER");

        mockMvc.perform(post("/api/ai/tasks/execute")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"task-1\",\"confirm\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("PERMISSION_DENIED"));

        verifyNoInteractions(aiTaskService);
    }

    @Test
    void shouldReturnCapabilityUnavailableWithoutCreatingFakePlan() throws Exception {
        when(jwtUtil.getRoleFromToken(TOKEN)).thenReturn("ADMIN");
        when(aiTaskService.plan(any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE));

        mockMvc.perform(post("/api/ai/tasks/plan")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"查看待办\",\"pageId\":\"dashboard\",\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AI_TASK_CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
