package com.aiworkmate.agent.gateway;

public interface ToolGateway {
    ToolGatewayResult execute(long stepId, WorkerLease lease);
}
