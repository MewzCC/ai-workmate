package com.aiworkmate.agent.gateway;

import com.fasterxml.jackson.databind.JsonNode;

public record ToolGatewayResult(
        GatewayDecision decision,
        GatewayDecisionCode code,
        JsonNode output,
        boolean outcomeUncertain
) {
    public ToolGatewayResult(GatewayDecision decision, GatewayDecisionCode code, JsonNode output) {
        this(decision, code, output, false);
    }

    public static ToolGatewayResult unavailable(GatewayDecisionCode code) {
        return new ToolGatewayResult(GatewayDecision.UNAVAILABLE, code, null, false);
    }

    public static ToolGatewayResult uncertain() {
        return new ToolGatewayResult(
                GatewayDecision.UNAVAILABLE, GatewayDecisionCode.TOOL_RESULT_UNKNOWN, null, true);
    }
}
