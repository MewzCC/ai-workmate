package com.aiworkmate.service.impl;

import com.aiworkmate.common.TraceContext;
import com.aiworkmate.entity.BusinessAuditLog;
import com.aiworkmate.mapper.BusinessAuditLogMapper;
import com.aiworkmate.service.BusinessAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BusinessAuditServiceImpl implements BusinessAuditService {

    private final BusinessAuditLogMapper mapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long tenantId,
                       Long actorUserId,
                       String resourceType,
                       String resourceId,
                       String action,
                       String result,
                       String summary) {
        insert(tenantId, actorUserId, resourceType, resourceId, action, result, summary);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordTransactional(Long tenantId,
                                    Long actorUserId,
                                    String resourceType,
                                    String resourceId,
                                    String action,
                                    String result,
                                    String summary) {
        insert(tenantId, actorUserId, resourceType, resourceId, action, result, summary);
    }

    private void insert(Long tenantId,
                        Long actorUserId,
                        String resourceType,
                        String resourceId,
                        String action,
                        String result,
                        String summary) {
        BusinessAuditLog audit = new BusinessAuditLog();
        audit.setTenantId(tenantId);
        audit.setActorUserId(actorUserId);
        audit.setResourceType(resourceType);
        audit.setResourceId(resourceId);
        audit.setAction(action);
        audit.setResult(result);
        audit.setSummary(summary == null ? null : summary.substring(0, Math.min(summary.length(), 500)));
        String traceId = TraceContext.traceId();
        audit.setTraceId(traceId == null
                ? UUID.randomUUID().toString().replace("-", "")
                : traceId);
        audit.setCreatedAt(LocalDateTime.now());
        mapper.insert(audit);
    }
}
