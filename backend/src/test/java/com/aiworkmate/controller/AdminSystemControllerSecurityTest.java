package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.SystemCapabilitiesResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.SystemCapabilityService;
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

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminSystemController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class,
        GlobalExceptionHandler.class})
class AdminSystemControllerSecurityTest {

    private static final String TOKEN = "system-capability-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SystemCapabilityService systemCapabilityService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus(TOKEN)).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(1001L);
    }

    @Test
    void shouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/admin/system/capabilities"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(systemCapabilityService);
    }

    @Test
    void shouldRejectUserWithoutManagementPermission() throws Exception {
        resolveUserWithPermissions(List.of("dashboard:read"));

        mockMvc.perform(get("/api/admin/system/capabilities")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());

        verifyNoInteractions(systemCapabilityService);
    }

    @Test
    void shouldAllowAccessManagementUser() throws Exception {
        resolveUserWithPermissions(List.of("access:manage"));
        when(systemCapabilityService.inspect()).thenReturn(
                new SystemCapabilitiesResponse(Instant.EPOCH, null, null, null, null, null));

        mockMvc.perform(get("/api/admin/system/capabilities")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        verify(systemCapabilityService).inspect();
    }

    private void resolveUserWithPermissions(List<String> permissions) {
        when(userAccessService.resolveActiveUser(1001L))
                .thenReturn(new ResolvedUserAccess(
                        1001L, "admin@example.com", 9L, "SYSTEM_ADMIN",
                        List.of("SYSTEM_ADMIN"), permissions, List.of("ALL"), 3L));
    }
}
