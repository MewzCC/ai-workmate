package com.aiworkmate.config;

import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.LocaleResolver;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Credentialed browser access requires an explicit server-controlled origin list. */
@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter(@Value("${app.cors.allowed-origins:}") String origins,
                                 ObjectMapper json, MessageSource messages, LocaleResolver locales) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOrigins(parseOrigins(origins));
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        // SSE 需要长连接支持
        config.addExposedHeader("Content-Type");
        config.addExposedHeader("Cache-Control");
        config.addExposedHeader(RequestTraceFilter.REQUEST_ID_HEADER);
        config.addExposedHeader(RequestTraceFilter.TRACE_ID_HEADER);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        CorsFilter filter = new CorsFilter(source);
        DefaultCorsProcessor processor = new DefaultCorsProcessor() {
            @Override protected void rejectRequest(ServerHttpResponse response) {
                // The wrapper writes the localized Result without reflecting the rejected origin.
                response.setStatusCode(HttpStatus.FORBIDDEN);
            }
        };
        filter.setCorsProcessor((configuration, request, response) -> {
            boolean allowed = processor.processRequest(configuration, request, response);
            if (!allowed) {
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                ErrorCode error = ErrorCode.PERMISSION_DENIED;
                json.writeValue(response.getWriter(), Result.error(error,
                        messages.getMessage(error.getMessageKey(), null, error.getMessageKey(), locales.resolveLocale(request))));
            }
            return allowed;
        });
        return filter;
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration(CorsFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        // Spring Security owns this filter's order, before JWT; do not register it twice.
        registration.setEnabled(false);
        return registration;
    }

    private static List<String> parseOrigins(String configured) {
        if (configured.isBlank()) return List.of();
        var origins = new ArrayList<String>();
        for (String entry : configured.split(",", -1)) {
            String origin = entry.strip();
            try {
                URI uri = URI.create(origin);
                int port = uri.getPort();
                String authority = uri.getHost() + (port == -1 ? "" : ":" + port);
                if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null
                        || uri.getUserInfo() != null || !authority.equals(uri.getRawAuthority())
                        || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || !"".equals(uri.getRawPath()) || port == 0 || port > 65535) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException invalid) {
                // Never include supplied configuration or the parser cause (may contain credentials).
                throw new IllegalArgumentException("app.cors.allowed-origins must contain exact HTTP(S) origins");
            }
            if (!origins.contains(origin)) origins.add(origin);
        }
        return List.copyOf(origins);
    }
}
