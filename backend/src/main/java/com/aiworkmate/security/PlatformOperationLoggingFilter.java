package com.aiworkmate.security;

import com.aiworkmate.service.PlatformOperationAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class PlatformOperationLoggingFilter extends OncePerRequestFilter {
    private final ObjectProvider<PlatformOperationAuditService> auditServiceProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            AuthenticatedUser actor = authenticatedUser();
            PlatformOperationAuditService auditService = auditServiceProvider.getIfAvailable();
            if (actor != null && auditService != null) {
                ClientRequestMetadata metadata = ClientRequestMetadata.from(request);
                auditService.recordRequest(actor, request.getMethod(), request.getRequestURI(),
                        failure == null ? response.getStatus() : 500,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                        metadata.clientIp(), metadata.userAgent(), failure);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !path.startsWith("/api/")
                || path.startsWith("/api/auth/login/");
    }

    private AuthenticatedUser authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser actor
                ? actor : null;
    }
}
