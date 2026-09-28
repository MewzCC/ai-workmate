package com.aiworkmate.security;

import com.aiworkmate.service.PlatformOperationAuditService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformOperationLoggingFilterTest {
    @Mock PlatformOperationAuditService auditService;
    @Mock ObjectProvider<PlatformOperationAuditService> auditServiceProvider;
    @Mock FilterChain chain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recordsAuthenticatedApiRequestAfterTheResponseCompletes() throws Exception {
        AuthenticatedUser actor = new AuthenticatedUser(7L, "operator", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/contracts/8");
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("User-Agent", "Browser");
        MockHttpServletResponse response = new MockHttpServletResponse();
        org.mockito.Mockito.when(auditServiceProvider.getIfAvailable()).thenReturn(auditService);
        PlatformOperationLoggingFilter filter = new PlatformOperationLoggingFilter(auditServiceProvider);

        filter.doFilter(request, response, chain);

        verify(auditService).recordRequest(eq(actor), eq("POST"), eq("/api/contracts/8"),
                eq(200), anyLong(), eq("10.0.0.8"), eq("Browser"), isNull());
    }

    @Test
    void skipsLoginEndpointBecauseControllerWritesTheResolvedLoginEvent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login/password");
        PlatformOperationLoggingFilter filter = new PlatformOperationLoggingFilter(auditServiceProvider);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(auditService, never()).recordRequest(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void recordsTheFinalRejectedStatusForAnAuthenticatedRead() throws Exception {
        AuthenticatedUser actor = new AuthenticatedUser(7L, "operator", 9L, "EMPLOYEE",
                List.of("EMPLOYEE"), List.of(), List.of("SELF"), 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor, null, List.of()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/runtime-logs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(auditServiceProvider.getIfAvailable()).thenReturn(auditService);
        org.mockito.Mockito.doAnswer(invocation -> {
            response.setStatus(403);
            return null;
        }).when(chain).doFilter(request, response);

        new PlatformOperationLoggingFilter(auditServiceProvider).doFilter(request, response, chain);

        verify(auditService).recordRequest(eq(actor), eq("GET"), eq("/api/admin/runtime-logs"),
                eq(403), anyLong(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), isNull());
    }
}
