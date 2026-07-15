package com.aiworkmate.config;

import com.aiworkmate.common.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveId(request.getHeader(REQUEST_ID_HEADER));
        String traceId = resolveTraceId(request.getHeader(TRACE_ID_HEADER), requestId);
        MDC.put(TraceContext.REQUEST_ID, requestId);
        MDC.put(TraceContext.TRACE_ID, traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceContext.REQUEST_ID);
            MDC.remove(TraceContext.TRACE_ID);
        }
    }

    private String resolveId(String candidate) {
        return StringUtils.hasText(candidate) && SAFE_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveTraceId(String candidate, String requestId) {
        return StringUtils.hasText(candidate) && SAFE_ID.matcher(candidate).matches() ? candidate : requestId;
    }
}
