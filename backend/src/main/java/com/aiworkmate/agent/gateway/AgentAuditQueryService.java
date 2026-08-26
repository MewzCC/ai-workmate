package com.aiworkmate.agent.gateway;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentAuditQueryService {
    private static final int MAX_QUERY_SIZE = 100;
    private final AgentToolInvocationMapper mapper;

    public AgentAuditQueryService(AgentToolInvocationMapper mapper) {
        this.mapper = mapper;
    }

    public List<AgentInvocationAuditRecord> findOwnedTaskAudit(long tenantId, long userId,
                                                                String taskNo, int requestedLimit) {
        if (tenantId <= 0 || userId <= 0 || taskNo == null || taskNo.isBlank()) {
            throw new IllegalArgumentException("Owned audit scope is required");
        }
        int limit = Math.max(1, Math.min(requestedLimit, MAX_QUERY_SIZE));
        return List.copyOf(mapper.selectOwnedTaskAudit(tenantId, userId, taskNo, limit));
    }
}
