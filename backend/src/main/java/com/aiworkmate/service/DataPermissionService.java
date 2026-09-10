package com.aiworkmate.service;

import com.aiworkmate.dto.*;
import java.util.Set;

public interface DataPermissionService {
    DataPermissionOverviewResponse overview(Long operatorUserId);
    DataPermissionPolicyResponse create(Long operatorUserId, SaveDataPermissionPolicyRequest request);
    DataPermissionPolicyResponse update(Long operatorUserId, Long id, SaveDataPermissionPolicyRequest request);
    void delete(Long operatorUserId, Long id, long version);
    void bindRole(Long operatorUserId, String roleCode, Long policyId);
    void bindUserException(Long operatorUserId, Long userId, Long policyId);
    void clearUserException(Long operatorUserId, Long userId);
    DataPermissionPreviewResponse preview(Long operatorUserId, Long userId);
    ResolvedDataPermission resolve(Long tenantId, Long userId);

    record ResolvedDataPermission(String source, Set<String> scopeTypes,
                                  Set<Long> departmentIds, Set<Long> visibleUserIds) {}
}
