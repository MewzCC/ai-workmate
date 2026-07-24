package com.aiworkmate.service;

import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.AccessUserResponse;
import com.aiworkmate.dto.SaveRouteRequest;

import java.util.Set;

public interface AccessControlService {

    AccessControlOverviewResponse overview();

    AccessUserResponse assignRole(Long operatorUserId, Long userId, String roleCode);

    AccessRoleResponse updateRolePermissions(Long operatorUserId, String roleCode, Set<String> permissionCodes);

    AccessRoleResponse createRole(Long operatorUserId, String code, String name, String description);

    AccessRouteResponse saveRoute(Long operatorUserId, SaveRouteRequest request);
}
