package com.aiworkmate.agent.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentOperationalObserver {

    public void workerRecovery(int closed, int recovered, int heartbeatUpdates) {
        if (closed == 0 && recovered == 0 && heartbeatUpdates == 0) return;
        log.info("event=agent_worker_recovery closed_count={} recovered_count={} heartbeat_count={}",
                closed, recovered, heartbeatUpdates);
    }

    public void retentionCompleted(int eventRows, int invocationRows, int taskRows, int tenantBatches) {
        log.info("event=agent_retention_cleanup event_count={} invocation_count={} task_count={} tenant_batch_count={}",
                eventRows, invocationRows, taskRows, tenantBatches);
    }

    public void retentionFailed(int completedTenantBatches) {
        log.warn("event=agent_retention_cleanup_failed completed_tenant_batch_count={}", completedTenantBatches);
    }
}
