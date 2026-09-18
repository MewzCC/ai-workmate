package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.PageActionOverviewResponse;
import com.aiworkmate.service.IntegrationEndpointService;
import com.aiworkmate.service.PageActionPolicyService;
import com.aiworkmate.service.RuntimeLogService;
import com.aiworkmate.service.SandboxReplayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PlatformOperationsAgentDomainToolAdapter implements PlatformOperationsToolPort {
    private final IntegrationEndpointService endpointService;
    private final PageActionPolicyService pageActionService;
    private final RuntimeLogService runtimeLogService;
    private final SandboxReplayService replayService;

    @Override public Page<Endpoint> endpoints(ToolActorContext c, EndpointQuery q) {
        if (q.endpointId() != null) return new Page<>(List.of(endpoint(endpointService.detail(c.userId(), q.endpointId()).endpoint())), 1, 1, 1);
        var result = endpointService.list(c.userId(), q.keyword(), q.status(), q.page(), q.size());
        return new Page<>(result.records().stream().map(this::endpoint).toList(), result.total(), result.page(), result.size());
    }
    @Override public Page<PageAction> pageActions(ToolActorContext c, PageActionQuery q) {
        var overview = pageActionService.overview(c.userId());
        List<PageActionOverviewResponse.Action> filtered = overview.pages().stream().flatMap(p -> p.actions().stream())
                .filter(a -> q.targetPageId() == null || q.targetPageId().equals(a.pageId()))
                .filter(a -> q.enabled() == null || q.enabled() == a.enabled()).toList();
        int from = Math.min(filtered.size(), (q.page() - 1) * q.size());
        int to = Math.min(filtered.size(), from + q.size());
        return new Page<>(filtered.subList(from, to).stream().map(this::pageAction).toList(), filtered.size(), q.page(), q.size());
    }
    @Override public Page<RuntimeLog> runtimeLogs(ToolActorContext c, RuntimeLogQuery q) {
        if (q.recordId() != null) return new Page<>(List.of(runtimeLog(runtimeLogService.detail(c.userId(), q.source(), q.recordId()))), 1, 1, 1);
        var result = runtimeLogService.query(c.userId(), q.source(), q.outcome(), q.keyword(), q.from(), q.to(), q.page(), q.size());
        return new Page<>(result.records().stream().map(this::runtimeLog).toList(), result.total(), result.page(), result.size());
    }
    @Override public Page<Replay> replays(ToolActorContext c, ReplayQuery q) {
        if (q.replayId() != null) return new Page<>(List.of(replay(replayService.detail(c.userId(), q.replayId()))), 1, 1, 1);
        var result = replayService.list(c.userId(), q.keyword(), q.status(), q.page(), q.size());
        return new Page<>(result.records().stream().map(this::replay).toList(), result.total(), result.page(), result.size());
    }
    private Endpoint endpoint(com.aiworkmate.dto.IntegrationEndpointResponse x) { return new Endpoint(x.id(), x.code(), x.name(), x.upstreamCode(), x.method(), x.relativePath(), x.description(), x.status(), x.version(), x.updatedAt(), x.canManage(), x.canExecute(), List.copyOf(x.allowedTransitions())); }
    private PageAction pageAction(PageActionOverviewResponse.Action x) { return new PageAction(x.pageId(), x.toolCode(), x.name(), x.description(), x.riskLevel(), x.sideEffect(), x.confirmationPolicy(), List.copyOf(x.requiredPermissions()), x.enabled(), x.explicitlyConfigured(), x.version()); }
    private RuntimeLog runtimeLog(com.aiworkmate.dto.RuntimeLogRecordResponse x) { return new RuntimeLog(x.source(), x.id(), x.referenceCode(), x.operation(), x.outcome(), x.decision(), x.statusCode(), x.durationMs(), x.operatorLabel(), x.errorCode(), null, null, null, x.startedAt(), x.completedAt()); }
    private RuntimeLog runtimeLog(com.aiworkmate.dto.RuntimeLogDetailResponse x) { return new RuntimeLog(x.source(), x.id(), x.referenceCode(), x.operation(), x.outcome(), x.decision(), x.statusCode(), x.durationMs(), x.operatorLabel(), x.errorCode(), x.handlerInvoked(), x.resultBytes(), x.attempt(), x.startedAt(), x.completedAt()); }
    private Replay replay(com.aiworkmate.dto.SandboxReplayRecordResponse x) { return new Replay(x.id(), x.sourceInvocationId(), x.endpointCode(), x.endpointName(), x.method(), x.relativePath(), x.baselineOutcome(), x.baselineHttpStatus(), x.status(), x.replayHttpStatus(), x.replayDurationMs(), null, x.comparisonResult(), x.requestedByLabel(), x.startedAt(), x.completedAt()); }
    private Replay replay(com.aiworkmate.dto.SandboxReplayDetailResponse x) { return new Replay(x.id(), x.sourceInvocationId(), x.endpointCode(), x.endpointName(), x.method(), x.relativePath(), x.baselineOutcome(), x.baselineHttpStatus(), x.status(), x.replayHttpStatus(), x.replayDurationMs(), x.replayErrorCode(), x.comparisonResult(), x.requestedByLabel(), x.startedAt(), x.completedAt()); }
}
