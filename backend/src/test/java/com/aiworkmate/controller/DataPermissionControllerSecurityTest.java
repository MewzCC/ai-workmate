package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.DataPermissionService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DataPermissionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class DataPermissionControllerSecurityTest {
    @Autowired MockMvc mockMvc; @MockBean DataPermissionService service; @MockBean JwtUtil jwtUtil; @MockBean UserAccessService userAccessService;
    @BeforeEach void setUp(){when(jwtUtil.validateTokenStatus("token")).thenReturn(JwtValidationStatus.VALID);when(jwtUtil.getUserIdFromToken("token")).thenReturn(10L);}
    @Test void rejectsAnonymous() throws Exception{mockMvc.perform(get("/api/admin/data-permissions")).andExpect(status().isUnauthorized());verifyNoInteractions(service);}
    @Test void rejectsMissingPermission() throws Exception{resolve(List.of("hr:read"));mockMvc.perform(get("/api/admin/data-permissions").header("Authorization","Bearer token")).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void usesAuthenticatedOperator() throws Exception{resolve(List.of("data-scope:manage"));mockMvc.perform(get("/api/admin/data-permissions").header("Authorization","Bearer token")).andExpect(status().isOk());verify(service).overview(10L);}
    private void resolve(List<String> permissions){when(userAccessService.resolveActiveUser(10L)).thenReturn(new ResolvedUserAccess(10L,"a",9L,"SYSTEM_ADMIN",List.of("SYSTEM_ADMIN"),permissions,List.of("ALL"),2L));}
}
