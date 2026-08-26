package com.aiworkmate.controller;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.AiTaskService;
import com.aiworkmate.agent.task.AgentTaskApiService;
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
import jakarta.servlet.http.Cookie;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

@WebMvcTest(AiTaskController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class AiTaskSecurityTest {

    private static final String TOKEN = "test-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AiTaskService aiTaskService;

    @MockBean
    private AgentTaskApiService agentTaskApiService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus(TOKEN)).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(1001L);
        when(userAccessService.resolveActiveUser(1001L))
                .thenReturn(new ResolvedUserAccess(1001L, "alice", "EMPLOYEE", java.util.List.of()));
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
    void shouldAllowAuthenticatedOwnerToQueueReadOnlyPlan() throws Exception {
        when(aiTaskService.execute(eq("00000000-0000-4000-8000-000000000001"), any(), eq("execute-key-123"), any()))
                .thenReturn(new com.aiworkmate.dto.AiTaskExecuteResponse(
                        "00000000-0000-4000-8000-000000000001", "QUEUED", "/status", "/events"));
        mockMvc.perform(post("/api/ai/tasks/00000000-0000-4000-8000-000000000001/execute")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "execute-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"planHash\":\"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("QUEUED"));

        verify(aiTaskService).execute(eq("00000000-0000-4000-8000-000000000001"), any(),
                eq("execute-key-123"), any());
    }

    @Test
    void shouldRemoveLegacyExecuteEndpoint() throws Exception {
        mockMvc.perform(post("/api/ai/tasks/execute")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"task-1\",\"confirm\":true}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void shouldReturnCapabilityUnavailableWithoutCreatingFakePlan() throws Exception {
        when(userAccessService.resolveActiveUser(1001L))
                .thenReturn(new ResolvedUserAccess(1001L, "alice", "SYSTEM_ADMIN", java.util.List.of()));
        when(aiTaskService.plan(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE));

        mockMvc.perform(post("/api/ai/tasks/plan")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "plan-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"查看待办\",\"pageId\":\"dashboard\",\"role\":\"SUPER_ADMIN\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("AI_TASK_CAPABILITY_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldRequireIndependentIdempotencyHeaders() throws Exception {
        mockMvc.perform(post("/api/ai/tasks/plan")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"查看待办\",\"pageId\":\"dashboard\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_INVALID"));

        mockMvc.perform(post("/api/ai/tasks/00000000-0000-4000-8000-000000000001/execute")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":1,\"planHash\":\"sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_INVALID"));

        verifyNoInteractions(aiTaskService);
    }

    @Test
    void shouldMapIdempotencyHashConflictToStable409() throws Exception {
        when(aiTaskService.plan(any(), eq("reused-key-123"), any()))
                .thenThrow(new com.aiworkmate.agent.task.IdempotencyConflictException());
        mockMvc.perform(post("/api/ai/tasks/plan")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "reused-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\":\"查看待办\",\"pageId\":\"dashboard\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void shouldRejectAnonymousEventSubscription() throws Exception {
        mockMvc.perform(get("/api/ai/tasks/00000000-0000-4000-8000-000000000001/events")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(agentTaskApiService);
    }

    @Test
    void shouldUseCookieAuthenticationAndLastEventHeaderForSse() throws Exception {
        when(agentTaskApiService.events(any(), eq("00000000-0000-4000-8000-000000000001"), eq(42L)))
                .thenReturn(new SseEmitter(60_000L));

        mockMvc.perform(get("/api/ai/tasks/00000000-0000-4000-8000-000000000001/events")
                        .cookie(new Cookie("oa_session", TOKEN))
                        .header("Last-Event-ID", "42")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(agentTaskApiService).events(any(),
                eq("00000000-0000-4000-8000-000000000001"), eq(42L));
    }

    @Test
    void shouldRejectMalformedConfirmationBeforeServiceCall() throws Exception {
        mockMvc.perform(post("/api/ai/tasks/00000000-0000-4000-8000-000000000001/confirmation-token")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planVersion\":0,\"planHash\":\"forged\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REQUEST_INVALID"));

        verifyNoInteractions(agentTaskApiService);
    }

    @Test
    void shouldReturnLocalizedSseErrorForMalformedResumeHeader() throws Exception {
        mockMvc.perform(get("/api/ai/tasks/00000000-0000-4000-8000-000000000001/events")
                        .cookie(new Cookie("oa_session", TOKEN))
                        .header("Last-Event-ID", "forged")
                        .header("Accept-Language", "en-US")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REQUEST_INVALID")));

        verifyNoInteractions(agentTaskApiService);
    }

    @Test
    void shouldHideCrossOwnerSseTaskAsNotFoundEvent() throws Exception {
        when(agentTaskApiService.events(any(), eq("00000000-0000-4000-8000-000000000099"), eq(0L)))
                .thenThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        mockMvc.perform(get("/api/ai/tasks/00000000-0000-4000-8000-000000000099/events")
                        .cookie(new Cookie("oa_session", TOKEN))
                        .header("Accept-Language", "zh-CN")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("RESOURCE_NOT_FOUND")));
    }

}
