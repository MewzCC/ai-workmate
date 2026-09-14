package com.aiworkmate.agent.tool.internal;

import com.aiworkmate.agent.registry.ToolCode;
import com.aiworkmate.agent.tool.port.PlatformOperationsToolPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import static com.aiworkmate.agent.tool.internal.BoundedToolArguments.*;

@Component
public final class SandboxReplayQueryToolHandler extends TypedReadToolHandler<PlatformOperationsToolPort.ReplayQuery, PlatformOperationsToolPort.Page<PlatformOperationsToolPort.Replay>> {
    private final PlatformOperationsToolPort port;
    public SandboxReplayQueryToolHandler(PlatformOperationsToolPort port, ObjectMapper mapper) { super(ToolCode.SANDBOX_REPLAY_QUERY, mapper); this.port = port; }
    @Override protected PlatformOperationsToolPort.ReplayQuery parseArguments(JsonNode a) { return new PlatformOperationsToolPort.ReplayQuery(optionalPositiveLong(a, "replayId"), optionalText(a, "keyword"), optionalText(a, "status"), positiveInt(a, "page", 1, 10000), positiveInt(a, "size", 20, 50)); }
    @Override protected PlatformOperationsToolPort.Page<PlatformOperationsToolPort.Replay> invoke(TrustedToolContext c, PlatformOperationsToolPort.ReplayQuery q) { return port.replays(c.actor(), q); }
}
