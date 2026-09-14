package com.aiworkmate.controller;

import com.aiworkmate.common.GlobalExceptionHandler;
import com.aiworkmate.config.RequestTraceFilter;
import com.aiworkmate.config.SecurityConfig;
import com.aiworkmate.dto.BudgetOptionsResponse;
import com.aiworkmate.security.JwtAuthenticationFilter;
import com.aiworkmate.security.JwtValidationStatus;
import com.aiworkmate.service.BudgetService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BudgetController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RequestTraceFilter.class, GlobalExceptionHandler.class})
class BudgetControllerSecurityTest {
 @Autowired MockMvc mvc; @MockBean BudgetService service; @MockBean JwtUtil jwtUtil; @MockBean UserAccessService accessService;
 @BeforeEach void setUp(){when(jwtUtil.validateTokenStatus("valid")).thenReturn(JwtValidationStatus.VALID);when(jwtUtil.getUserIdFromToken("valid")).thenReturn(42L);when(accessService.resolveActiveUser(42L)).thenReturn(new ResolvedUserAccess(42L,"finance",9L,"FINANCE_ADMIN",List.of("FINANCE_ADMIN"),List.of("route:budget","budget:manage"),List.of("TENANT"),2L));}
 @Test void endpointRequiresAuthentication()throws Exception{mvc.perform(get("/api/budgets")).andExpect(status().isUnauthorized());}
 @Test void optionsUsesAuthenticatedIdentity()throws Exception{when(service.options(42L)).thenReturn(new BudgetOptionsResponse(List.of()));mvc.perform(get("/api/budgets/options").header("Authorization","Bearer valid")).andExpect(status().isOk());verify(service).options(42L);}
 @Test void malformedTransactionIsRejectedBeforeService()throws Exception{mvc.perform(post("/api/budgets/8/transactions").header("Authorization","Bearer valid").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DELETE\",\"amount\":0,\"version\":1}")).andExpect(status().isBadRequest());verify(service,never()).operate(anyLong(),anyLong(),any());}
}
