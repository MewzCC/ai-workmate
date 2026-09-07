package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.WorkbenchPageResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.WorkbenchRecordService;
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

@WebMvcTest(WorkbenchRecordController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class WorkbenchRecordControllerSecurityTest {
    private static final String TOKEN = "workbench-token";

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private WorkbenchRecordService service;
    @MockBean
    private JwtUtil jwtUtil;
    @MockBean
    private UserAccessService userAccessService;

    @BeforeEach
    void setUp() {
        when(jwtUtil.validateTokenStatus(TOKEN)).thenReturn(JwtValidationStatus.VALID);
        when(jwtUtil.getUserIdFromToken(TOKEN)).thenReturn(1001L);
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(new ResolvedUserAccess(
                1001L, "finance@example.com", 9L, "FINANCE_ADMIN", List.of("FINANCE_ADMIN"),
                List.of("route:expense", "workbench:finance:manage"), List.of("TENANT"), 1L));
    }

    @Test
    void shouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(get("/api/workbench/modules/expense/records"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUIRED"));
        verifyNoInteractions(service);
    }

    @Test
    void shouldUseAuthenticatedUserForModuleQuery() throws Exception {
        when(service.list(1001L, "expense", null, null, 1, 20))
                .thenReturn(new WorkbenchPageResponse(List.of(), 0, 1, 20, true));

        mockMvc.perform(get("/api/workbench/modules/expense/records")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canManage").value(true));

        verify(service).list(1001L, "expense", null, null, 1, 20);
    }
}
