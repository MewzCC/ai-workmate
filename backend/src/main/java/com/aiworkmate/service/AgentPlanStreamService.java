package com.aiworkmate.service;

import com.aiworkmate.agent.planner.PlannerReplyStream;
import com.aiworkmate.common.*;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

@Service
public class AgentPlanStreamService {
    private final AiTaskService tasks;
    private final Executor executor;
    public AgentPlanStreamService(AiTaskService tasks, @Qualifier("agentPlanStreamExecutor") Executor executor) {
        this.tasks = tasks; this.executor = executor;
    }

    public Flux<ServerSentEvent<Object>> plan(AiTaskPlanRequest request, String key, AuthenticatedUser user) {
        var locale = LocaleContextHolder.getLocale();
        var loggingContext = org.slf4j.MDC.getCopyOfContextMap();
        String requestId = TraceContext.requestId(), traceId = TraceContext.traceId();
        return Flux.create(sink -> {
            FutureTask<Void> work = new FutureTask<>(() -> {
                var previous = LocaleContextHolder.getLocaleContext();
                var previousLogging = org.slf4j.MDC.getCopyOfContextMap();
                LocaleContextHolder.setLocale(locale);
                if (loggingContext != null) org.slf4j.MDC.setContextMap(loggingContext);
                else org.slf4j.MDC.clear();
                try {
                    sink.next(event("planning", Map.of("status", "PLANNING")));
                    var result = PlannerReplyStream.observe(text -> {
                        if (sink.isCancelled()) throw new java.util.concurrent.CancellationException();
                        sink.next(event("reply", Map.of("text", text)));
                    }, () -> tasks.plan(request, key, user));
                    if (!sink.isCancelled()) { sink.next(event("plan", result)); sink.complete(); }
                } catch (Exception error) {
                    if (!sink.isCancelled()) {
                        BusinessException business = error instanceof BusinessException b ? b : new BusinessException(ErrorCode.SYSTEM_ERROR);
                        sink.next(event("error", Map.of("message", business.getMessage(), "code", business.getCode(),
                                "errorCode", business.getErrorCode() == null ? "SYSTEM_ERROR" : business.getErrorCode(), "status", business.getStatus().value(),
                                "requestId", requestId == null ? "" : requestId, "traceId", traceId == null ? "" : traceId)));
                        sink.complete();
                    }
                } finally {
                    LocaleContextHolder.setLocaleContext(previous);
                    if (previousLogging == null) org.slf4j.MDC.clear(); else org.slf4j.MDC.setContextMap(previousLogging);
                }
                return null;
            });
            sink.onDispose(() -> work.cancel(true));
            try { executor.execute(work); }
            catch (RuntimeException busy) { sink.error(new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE)); }
        });
    }

    private static ServerSentEvent<Object> event(String type, Object value) {
        return ServerSentEvent.builder(value).event(type).build();
    }
}
