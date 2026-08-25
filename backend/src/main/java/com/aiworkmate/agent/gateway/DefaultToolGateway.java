package com.aiworkmate.agent.gateway;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultToolGateway implements ToolGateway {
    private final AgentRuntimeProperties runtimeProperties;

    @Override
    public ToolGatewayResult execute(long stepId, WorkerLease lease) {
        if (!runtimeProperties.isEnabled() || !runtimeProperties.isExecutionEnabled()) {
            return ToolGatewayResult.unavailable(GatewayDecisionCode.GATEWAY_DISABLED);
        }
        return ToolGatewayResult.unavailable(GatewayDecisionCode.GATEWAY_UNAVAILABLE);
    }
}
