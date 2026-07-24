package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.UserProfileService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class UserProfileSecurityTest {

    private static final String TOKEN = "profile-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserProfileService userProfileService;

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
    void shouldRejectAnonymousProfileUpdate() throws Exception {
        mockMvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userProfileService);
    }

    @Test
    void shouldUseAuthenticatedUserIdForProfileUpdate() throws Exception {
        when(userProfileService.update(org.mockito.ArgumentMatchers.eq(1001L), any()))
                .thenReturn(new AuthUserResponse(1001L, "Alice Chen", "alice@example.com",
                        "USER", null));

        mockMvc.perform(put("/api/profile")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice Chen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Alice Chen"));

        verify(userProfileService).update(org.mockito.ArgumentMatchers.eq(1001L), any());
    }
}
