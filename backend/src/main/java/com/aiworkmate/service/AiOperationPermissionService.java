package com.aiworkmate.service;

import com.aiworkmate.dto.AiOperationPermissionOverviewResponse;
import com.aiworkmate.security.AuthenticatedUser;

import java.util.Set;

public interface AiOperationPermissionService {
    AiOperationPermissionOverviewResponse overview(AuthenticatedUser operator);
    AiOperationPermissionOverviewResponse updateTenantPolicy(AuthenticatedUser operator,
                                                              boolean enabled,
                                                              boolean writeToolsEnabled);
    AiOperationPermissionOverviewResponse updateToolStatus(AuthenticatedUser operator,
                                                            String toolCode,
                                                            boolean enabled);
    AiOperationPermissionOverviewResponse updateRoleTools(AuthenticatedUser operator,
                                                          String roleCode,
                                                          Set<String> toolCodes);
}
