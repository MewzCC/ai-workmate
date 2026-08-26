package com.aiworkmate.agent.retention;

import com.aiworkmate.agent.config.AgentRuntimeProperties;
import com.aiworkmate.agent.observability.AgentOperationalObserver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.ToIntFunction;

@Component
public class AgentRetentionCleaner {
    private static final int MAX_TENANTS_PER_RUN = 100;

    private final AgentRuntimeProperties properties;
    private final AgentRetentionMapper mapper;
    private final AgentOperationalObserver observer;
    private final Clock clock;

    @Autowired
    public AgentRetentionCleaner(AgentRuntimeProperties properties, AgentRetentionMapper mapper,
                                 AgentOperationalObserver observer) {
        this(properties, mapper, observer, Clock.systemDefaultZone());
    }

    AgentRetentionCleaner(AgentRuntimeProperties properties, AgentRetentionMapper mapper,
                          AgentOperationalObserver observer, Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.observer = observer;
        this.clock = clock;
    }

    @Scheduled(cron = "${agent.retention-cleanup-cron:0 15 4 * * *}")
    public void clean() {
        if (!properties.isRetentionCleanupEnabled()) return;
        int batches = 0;
        try {
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime detailCutoff = now.minusDays(properties.getLimits().getEventRetentionDays());
            LocalDateTime taskCutoff = now.minusDays(properties.getLimits().getTaskRetentionDays());
            List<Long> eventTenants = mapper.selectEventTenants(detailCutoff, MAX_TENANTS_PER_RUN);
            int events = deletePerTenant(eventTenants,
                    tenant -> mapper.deleteEventBatch(tenant, detailCutoff, properties.getRetentionBatchSize()));
            batches += eventTenants.size();
            List<Long> invocationTenants = mapper.selectInvocationTenants(detailCutoff, MAX_TENANTS_PER_RUN);
            int invocations = deletePerTenant(invocationTenants,
                    tenant -> mapper.deleteInvocationBatch(tenant, detailCutoff, properties.getRetentionBatchSize()));
            batches += invocationTenants.size();
            List<Long> taskTenants = mapper.selectTaskTenants(taskCutoff, MAX_TENANTS_PER_RUN);
            int tasks = deletePerTenant(taskTenants,
                    tenant -> mapper.deleteTaskBatch(tenant, taskCutoff, properties.getRetentionBatchSize()));
            batches += taskTenants.size();
            observer.retentionCompleted(events, invocations, tasks, batches);
        } catch (RuntimeException exception) {
            observer.retentionFailed(batches);
            throw exception;
        }
    }

    private int deletePerTenant(List<Long> tenants, ToIntFunction<Long> delete) {
        int deleted = 0;
        for (Long tenant : tenants) deleted += delete.applyAsInt(tenant);
        return deleted;
    }
}
