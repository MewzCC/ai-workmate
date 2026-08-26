package com.aiworkmate.agent.gateway;

public interface GatewayAuditWriter {
    String record(GatewayExecutionSnapshot snapshot,
                  GatewayDecision decision,
                  String decisionCode,
                  boolean handlerInvoked);

    void complete(String decisionId,
                  boolean handlerInvoked,
                  String outcome,
                  Integer resultBytes,
                  String errorClass,
                  long durationMs);
}
