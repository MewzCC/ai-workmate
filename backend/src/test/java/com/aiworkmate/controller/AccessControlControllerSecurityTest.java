package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.AccessControlService;
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
import java.util.Set;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccessControlController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class,
        GlobalExceptionHandler.class})
class AccessControlControllerSecurityTest {

    private static final String TOKEN = "access-control-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccessControlService accessControlService;

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
    void shouldRejectAnonymousRoleMemberUpdate() throws Exception {
        mockMvc.perform(put("/api/admin/access-control/roles/HR_MANAGER/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[21,22]}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accessControlService);
    }

    @Test
    void shouldRejectUserWithoutAccessManagementPermission() throws Exception {
        resolveUserWithPermissions(List.of("dashboard:read"));

        mockMvc.perform(put("/api/admin/access-control/roles/HR_MANAGER/members")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[21,22]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(accessControlService);
    }

    @Test
    void shouldUseAuthenticatedOperatorAndTenantForRoleMemberUpdate() throws Exception {
        resolveUserWithPermissions(List.of("access:manage"));

        mockMvc.perform(put("/api/admin/access-control/roles/hr_manager/members")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[21,22]}"))
                .andExpect(status().isOk());

        verify(accessControlService).updateRoleMembers(
                1001L, 9L, "hr_manager", Set.of(21L, 22L));
    }

    @Test
    void shouldRejectInvalidRoleMemberIds() throws Exception {
        resolveUserWithPermissions(List.of("access:manage"));

        mockMvc.perform(put("/api/admin/access-control/roles/HR_MANAGER/members")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[0]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(accessControlService);
    }

    private void resolveUserWithPermissions(List<String> permissions) {
        when(userAccessService.resolveActiveUser(1001L))
                .thenReturn(new ResolvedUserAccess(
                        1001L, "admin@example.com", 9L, "SYSTEM_ADMIN",
                        List.of("SYSTEM_ADMIN"), permissions, List.of("ALL"), 3L));
    }
}
