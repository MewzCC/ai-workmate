package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessControlOverviewResponse;
import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.mapper.AccessControlMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceImplTest {

    @Mock
    private AccessControlMapper accessControlMapper;

    @InjectMocks
    private AccessControlServiceImpl accessControlService;

    @Test
    void shouldPreventDemotingLastSuperAdministrator() {
        when(accessControlMapper.selectUserTenantId(1L)).thenReturn(1L);
        when(accessControlMapper.countRoleForTenant(1L, "EMPLOYEE")).thenReturn(1);
        when(accessControlMapper.selectUserRoleCodes(1L, 1L)).thenReturn(java.util.List.of("SUPER_ADMIN"));
        when(accessControlMapper.countActiveSuperAdminsForTenant(1L)).thenReturn(1);

        assertThatThrownBy(() -> accessControlService.assignRole(1L, 1L, "EMPLOYEE"))
                .isInstanceOf(BusinessException.class);

        verify(accessControlMapper, never()).deleteUserRoles(1L, 1L);
    }

    @Test
    void shouldAllowCreatingCustomRole() {
        when(accessControlMapper.selectUserTenantId(1L)).thenReturn(1L);
        when(accessControlMapper.countRoleForTenant(1L, "SALES_MANAGER")).thenReturn(0);

        accessControlService.createRole(1L, "sales_manager", "销售主管", "管理销售团队");

        verify(accessControlMapper).insertRoleForTenant(
                1L, "SALES_MANAGER", "销售主管", "管理销售团队");
        verify(accessControlMapper).insertAudit(
                1L, "CREATE_ROLE", "ROLE", "SALES_MANAGER", null, "销售主管");
    }

    @Test
    void shouldUpdateRolePermissionsWithinOperatorTenantAndInvalidateUserAccess() {
        Set<String> requested = Set.of("hr:read", "access:manage");
        AccessRoleResponse role = new AccessRoleResponse(
                "HR_MANAGER", "人事经理", "维护组织与人员权限", false);
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "HR_MANAGER")).thenReturn(1);
        when(accessControlMapper.selectAllPermissionCodesForTenant(3L))
                .thenReturn(List.of("hr:read", "access:manage", "route:access-control"));
        when(accessControlMapper.selectPermissionCodesForRoles(3L, List.of("HR_MANAGER")))
                .thenReturn(List.of("hr:read"));
        when(accessControlMapper.selectRolesForTenant(3L)).thenReturn(List.of(role));

        AccessRoleResponse result = accessControlService.updateRolePermissions(
                7L, "hr_manager", requested);

        assertThat(result.code()).isEqualTo("HR_MANAGER");
        assertThat(result.permissions()).containsExactlyInAnyOrderElementsOf(requested);
        verify(accessControlMapper).deleteRolePermissionsForTenant(3L, "HR_MANAGER");
        verify(accessControlMapper).insertRolePermissionsForTenant(3L, "HR_MANAGER", requested);
        verify(accessControlMapper).insertAudit(
                7L,
                "UPDATE_ROLE_PERMISSIONS",
                "ROLE",
                "HR_MANAGER",
                "[hr:read]",
                "[access:manage, hr:read]");
        verify(accessControlMapper).incrementPermissionVersionForRole(3L, "HR_MANAGER");
    }

    @Test
    void shouldRejectUnknownPermissionWithoutChangingRolePermissions() {
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "HR_MANAGER")).thenReturn(1);
        when(accessControlMapper.selectAllPermissionCodesForTenant(3L))
                .thenReturn(List.of("hr:read"));

        assertThatThrownBy(() -> accessControlService.updateRolePermissions(
                7L, "HR_MANAGER", Set.of("unknown:permission")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在的权限编码");

        verify(accessControlMapper, never())
                .deleteRolePermissionsForTenant(3L, "HR_MANAGER");
        verify(accessControlMapper, never())
                .incrementPermissionVersionForRole(3L, "HR_MANAGER");
    }

    @Test
    void shouldRejectChangingSuperAdministratorPermissions() {
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "SUPER_ADMIN")).thenReturn(1);

        assertThatThrownBy(() -> accessControlService.updateRolePermissions(
                7L, "SUPER_ADMIN", Set.of("hr:read")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超级管理员始终拥有全部权限");

        verify(accessControlMapper, never())
                .deleteRolePermissionsForTenant(3L, "SUPER_ADMIN");
    }

    @Test
    void shouldReplaceRoleMembersAndRefreshPrimaryRolesAndPermissionVersions() {
        AccessRoleResponse role = new AccessRoleResponse(
                "HR_MANAGER", "人事经理", "维护组织与人员权限", false);
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "HR_MANAGER")).thenReturn(1);
        when(accessControlMapper.selectRoleMemberUserIds(3L, "HR_MANAGER"))
                .thenReturn(List.of(10L, 20L));
        when(accessControlMapper.selectUsers(3L)).thenReturn(List.of(
                userRow(10L, 1), userRow(20L, 0), userRow(30L, 1)));
        when(accessControlMapper.selectUserRoleCodes(3L, 10L))
                .thenReturn(List.of("HR_MANAGER", "EMPLOYEE"));
        when(accessControlMapper.selectUserRoleCodes(3L, 20L))
                .thenReturn(List.of("HR_MANAGER", "EMPLOYEE"));
        when(accessControlMapper.selectUserRoleCodes(3L, 30L))
                .thenReturn(List.of("EMPLOYEE"));
        when(accessControlMapper.selectRolesForTenant(3L)).thenReturn(List.of(role));
        when(accessControlMapper.selectPermissionCodesForRoles(3L, List.of("HR_MANAGER")))
                .thenReturn(List.of("hr:read"));

        AccessControlOverviewResponse result = accessControlService.updateRoleMembers(
                7L, 3L, "hr_manager", Set.of(30L));

        assertThat(result.roles()).extracting(AccessRoleResponse::code)
                .containsExactly("HR_MANAGER");
        assertThat(result.roles().get(0).permissions()).containsExactly("hr:read");
        verify(accessControlMapper).insertUserRoles(3L, 10L, Set.of("EMPLOYEE"));
        verify(accessControlMapper).insertUserRoles(3L, 20L, Set.of("EMPLOYEE"));
        verify(accessControlMapper).insertUserRoles(
                3L, 30L, Set.of("EMPLOYEE", "HR_MANAGER"));
        verify(accessControlMapper).updateUserRolesVersion(3L, 10L, "EMPLOYEE");
        verify(accessControlMapper).updateUserRolesVersion(3L, 20L, "EMPLOYEE");
        verify(accessControlMapper).updateUserRolesVersion(3L, 30L, "EMPLOYEE");
        verify(accessControlMapper).insertAudit(
                7L, "UPDATE_ROLE_MEMBERS", "ROLE", "HR_MANAGER",
                "[10, 20]", "[30]");
    }

    @Test
    void shouldAllowRemovingDisabledMemberButRejectAddingDisabledUser() {
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "HR_MANAGER")).thenReturn(1);
        when(accessControlMapper.selectRoleMemberUserIds(3L, "HR_MANAGER"))
                .thenReturn(List.of());
        when(accessControlMapper.selectUsers(3L)).thenReturn(List.of(userRow(20L, 0)));
        when(accessControlMapper.selectUserRoleCodes(3L, 20L))
                .thenReturn(List.of("EMPLOYEE"));

        assertThatThrownBy(() -> accessControlService.updateRoleMembers(
                7L, 3L, "HR_MANAGER", Set.of(20L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("停用用户不能新增到角色");

        verify(accessControlMapper, never()).deleteUserRoles(3L, 20L);
        verify(accessControlMapper, never()).insertAudit(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldPreventRemovingUsersOnlyRole() {
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "HR_MANAGER")).thenReturn(1);
        when(accessControlMapper.selectRoleMemberUserIds(3L, "HR_MANAGER"))
                .thenReturn(List.of(10L));
        when(accessControlMapper.selectUsers(3L)).thenReturn(List.of(userRow(10L, 1)));
        when(accessControlMapper.selectUserRoleCodes(3L, 10L))
                .thenReturn(List.of("HR_MANAGER"));

        assertThatThrownBy(() -> accessControlService.updateRoleMembers(
                7L, 3L, "HR_MANAGER", Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("唯一角色");

        verify(accessControlMapper, never()).deleteUserRoles(3L, 10L);
    }

    @Test
    void shouldPreventRemovingLastActiveSuperAdministrator() {
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "SUPER_ADMIN")).thenReturn(1);
        when(accessControlMapper.selectRoleMemberUserIds(3L, "SUPER_ADMIN"))
                .thenReturn(List.of(10L, 20L));
        when(accessControlMapper.selectUsers(3L)).thenReturn(List.of(
                userRow(10L, 1), userRow(20L, 0)));
        when(accessControlMapper.selectUserRoleCodes(3L, 10L))
                .thenReturn(List.of("SUPER_ADMIN", "EMPLOYEE"));
        when(accessControlMapper.selectUserRoleCodes(3L, 20L))
                .thenReturn(List.of("SUPER_ADMIN", "EMPLOYEE"));

        assertThatThrownBy(() -> accessControlService.updateRoleMembers(
                7L, 3L, "SUPER_ADMIN", Set.of(20L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("至少保留一名有效超级管理员");

        verify(accessControlMapper, never()).deleteUserRoles(3L, 10L);
    }

    @Test
    void shouldRejectRoleMemberFromAnotherTenant() {
        when(accessControlMapper.selectUserTenantId(7L)).thenReturn(3L);
        when(accessControlMapper.countRoleForTenant(3L, "HR_MANAGER")).thenReturn(1);
        when(accessControlMapper.selectRoleMemberUserIds(3L, "HR_MANAGER"))
                .thenReturn(List.of());
        when(accessControlMapper.selectUsers(3L)).thenReturn(List.of());

        assertThatThrownBy(() -> accessControlService.updateRoleMembers(
                7L, 3L, "HR_MANAGER", Set.of(99L)))
                .isInstanceOf(BusinessException.class);

        verify(accessControlMapper, never()).deleteUserRoles(3L, 99L);
    }

    private AccessUserRow userRow(Long id, Integer status) {
        return new AccessUserRow(
                id, "用户" + id, "user" + id + "@example.com", "EMPLOYEE",
                status, 3L, null, null, null, 1L, null);
    }
}
