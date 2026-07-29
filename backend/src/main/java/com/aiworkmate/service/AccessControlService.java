package com.aiworkmate.service;

import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.AccessUserResponse;
import com.aiworkmate.dto.SaveRouteRequest;

import java.util.Set;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.PositionResponse;

public interface AccessControlService {

    AccessControlOverviewResponse overview();

    AccessControlOverviewResponse overview(Long tenantId);

    AccessUserResponse assignRole(Long operatorUserId, Long userId, String roleCode);

    AccessUserResponse assignRoles(Long operatorUserId, Long tenantId, Long userId, Set<String> roleCodes);

    AccessUserResponse updateUserOrganization(Long operatorUserId, Long tenantId, Long userId,
                                              Long departmentId, Long positionId, Long approverUserId);

    AccessUserResponse updateUserStatus(Long operatorUserId, Long tenantId, Long userId, Integer status);

    DepartmentResponse saveDepartment(Long operatorUserId, Long tenantId, String code,
                                      String name, Long parentId, Long defaultApproverUserId);

    PositionResponse savePosition(Long operatorUserId, Long tenantId, String code, String name);

    void deleteDepartment(Long operatorUserId, Long tenantId, Long departmentId);

    void deletePosition(Long operatorUserId, Long tenantId, Long positionId);

    AccessRoleResponse updateRolePermissions(Long operatorUserId, String roleCode, Set<String> permissionCodes);

    AccessRoleResponse createRole(Long operatorUserId, String code, String name, String description);

    AccessRouteResponse saveRoute(Long operatorUserId, SaveRouteRequest request);
}
