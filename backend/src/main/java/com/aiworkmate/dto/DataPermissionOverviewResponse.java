package com.aiworkmate.dto;

import java.util.List;

public record DataPermissionOverviewResponse(
        List<DataPermissionPolicyResponse> policies,
        List<AccessRoleResponse> roles,
        List<AccessUserResponse> users,
        List<DepartmentResponse> departments,
        List<DataPermissionRoleBindingResponse> roleBindings,
        List<DataPermissionUserExceptionResponse> userExceptions) {
}
