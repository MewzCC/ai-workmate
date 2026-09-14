package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.IntegrationOptionsResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.IntegrationEndpointService;
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
import static org.mockito.ArgumentMatchers.*;import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntegrationEndpointController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class IntegrationEndpointControllerSecurityTest {
 @Autowired MockMvc mvc;@MockBean IntegrationEndpointService service;@MockBean JwtUtil jwtUtil;@MockBean UserAccessService accessService;
 @BeforeEach void setup(){when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);when(accessService.resolveActiveUser(42L)).thenReturn(new ResolvedUserAccess(42L,"system",9L,"SYSTEM_ADMIN",List.of("SYSTEM_ADMIN"),List.of("route:api-center","integration:endpoint:manage","integration:endpoint:execute"),List.of("TENANT"),1L));}
 @Test void endpointRequiresAuthentication()throws Exception{mvc.perform(get("/api/integration/endpoints")).andExpect(status().isUnauthorized());}
 @Test void optionsUsesAuthenticatedIdentity()throws Exception{when(service.options(42L)).thenReturn(new IntegrationOptionsResponse(List.of()));mvc.perform(get("/api/integration/endpoints/options").header("Authorization","Bearer valid")).andExpect(status().isOk());verify(service).options(42L);}
 @Test void rejectsAbsoluteTargetBeforeService()throws Exception{mvc.perform(post("/api/integration/endpoints").header("Authorization","Bearer valid").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"api-1\",\"name\":\"API\",\"upstreamCode\":\"primary-sandbox\",\"method\":\"GET\",\"relativePath\":\"\"}")).andExpect(status().isBadRequest());verify(service,never()).create(anyLong(),any());}
}
