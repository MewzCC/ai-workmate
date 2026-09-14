package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Typed boundary that can be implemented by local services or future platform-service clients. */
public interface PlatformOperationsToolPort {
    Page<Endpoint> endpoints(ToolActorContext context, EndpointQuery query);
    Page<PageAction> pageActions(ToolActorContext context, PageActionQuery query);
    Page<RuntimeLog> runtimeLogs(ToolActorContext context, RuntimeLogQuery query);
    Page<Replay> replays(ToolActorContext context, ReplayQuery query);

    record EndpointQuery(Long endpointId, String keyword, String status, int page, int size) { }
    record PageActionQuery(String targetPageId, Boolean enabled, int page, int size) { }
    record RuntimeLogQuery(String source, Long recordId, String outcome, String keyword,
                           LocalDateTime from, LocalDateTime to, int page, int size) { }
    record ReplayQuery(Long replayId, String keyword, String status, int page, int size) { }
    record Page<T>(List<T> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Endpoint(long id, String code, String name, String upstreamCode, String method,
                    String relativePath, String description, String status, int version,
                    LocalDateTime updatedAt, boolean canManage, boolean canExecute,
                    List<String> allowedTransitions) { }
    record PageAction(String pageId, String toolCode, String name, String description,
                      String riskLevel, String sideEffect, String confirmationPolicy,
                      List<String> requiredPermissions, boolean enabled,
                      boolean explicitlyConfigured, int version) { }
    record RuntimeLog(String source, long id, String referenceCode, String operation,
                      String outcome, String decision, Integer statusCode, Long durationMs,
                      String operatorLabel, String errorCode, Boolean handlerInvoked,
                      Integer resultBytes, Integer attempt, LocalDateTime startedAt,
                      LocalDateTime completedAt) { }
    record Replay(long id, long sourceInvocationId, String endpointCode, String endpointName,
                  String method, String relativePath, String baselineOutcome,
                  Integer baselineHttpStatus, String status, Integer replayHttpStatus,
                  Long replayDurationMs, String replayErrorCode, String comparisonResult,
                  String requestedByLabel, LocalDateTime startedAt, LocalDateTime completedAt) { }
}
