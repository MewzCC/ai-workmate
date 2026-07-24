package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.ChatService;
import com.aiworkmate.service.model.ChatChunk;
import com.aiworkmate.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Flux;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class ChatStreamSecurityTest {

    private static final String TOKEN = "stream-token";
    private static final String REQUEST_BODY = """
            {
              "conversationId": 2001,
              "message": "hello",
              "model": "deepseek-v4-flash",
              "attachmentIds": [],
              "maxContextRounds": 10
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus(TOKEN)).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(1001L);
        when(jwtUtil.getUsernameFromToken(TOKEN)).thenReturn("alice");
        when(jwtUtil.getRoleFromToken(TOKEN)).thenReturn("USER");
    }

    @Test
    void shouldKeepAuthenticatedStreamAvailableDuringAsyncDispatch() throws Exception {
        when(chatService.chatStream(anyLong(), anyString(), anyLong(), anyString(),
                anyString(), anyList(), anyInt()))
                .thenReturn(Flux.just(
                        ChatChunk.metadata(2001L, 3001L),
                        ChatChunk.delta("stream-ok", 2001L, 3001L)
                ));

        MvcResult result = mockMvc.perform(post("/api/chat/stream")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("stream-ok")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")));
    }

    @Test
    void shouldRejectAnonymousStreamBeforeAsyncProcessing() throws Exception {
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(chatService);
    }
}
