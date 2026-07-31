package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.KnowledgeService;
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

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KnowledgeController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class KnowledgeControllerSecurityTest {

    private static final String TOKEN = "knowledge-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KnowledgeService knowledgeService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus(TOKEN)).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(1001L);
        when(userAccessService.resolveActiveUser(1001L))
                .thenReturn(new ResolvedUserAccess(1001L, "alice", "EMPLOYEE", List.of()));
    }

    @Test
    void shouldRejectAnonymousKnowledgeUpload() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"policy.txt\",\"content\":\"policy\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(RequestTraceFilter.REQUEST_ID_HEADER));

        verifyNoInteractions(knowledgeService);
    }

    @Test
    void shouldRejectInvalidDocumentBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/knowledge/documents")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filename\":\"\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(knowledgeService);
    }
}
