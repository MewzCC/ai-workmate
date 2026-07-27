package com.aiworkmate.service;

public interface BusinessAuditService {

    void record(Long tenantId,
                Long actorUserId,
                String resourceType,
                String resourceId,
                String action,
                String result,
                String summary);
}
