package com.aiworkmate.security;

import com.aiworkmate.util.JwtUtil;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

    private final JwtUtil jwtUtil;
    private final UserAccessService userAccessService;

    @Value("${app.auth.cookie-name:oa_session}")
    private String cookieName;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            JwtValidationStatus validationStatus = jwtUtil.validateTokenStatus(token);
            if (validationStatus != JwtValidationStatus.VALID) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, validationStatus);
                return;
            }

            ResolvedUserAccess access = userAccessService.resolveActiveUser(jwtUtil.getUserIdFromToken(token));
            if (access == null) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, JwtValidationStatus.INVALID);
                return;
            }
            AuthenticatedUser principal = new AuthenticatedUser(access.userId(), access.username(), access.role());
            var authorities = new ArrayList<SimpleGrantedAuthority>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + access.role()));
            access.permissions().stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, JwtValidationStatus.INVALID);
            if (log.isDebugEnabled()) {
                log.debug("JWT authentication failed: {}", ex.getMessage());
            }
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
