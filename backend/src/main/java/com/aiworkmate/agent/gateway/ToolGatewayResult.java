package com.aiworkmate.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolGatewayResult(
        GatewayDecision decision,
        GatewayDecisionCode code,
        JsonNode output
) {
    public static ToolGatewayResult unavailable(GatewayDecisionCode code) {
        return new ToolGatewayResult(GatewayDecision.UNAVAILABLE, code, null);
    }
}
