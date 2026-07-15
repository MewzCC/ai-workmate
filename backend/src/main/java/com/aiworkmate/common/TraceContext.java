package com.aiworkmate.common;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String REQUEST_ID = "requestId";
    public static final String TRACE_ID = "traceId";

    private TraceContext() {
    }

    public static String requestId() {
        return MDC.get(REQUEST_ID);
    }

    public static String traceId() {
        return MDC.get(TRACE_ID);
    }
}
