package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.mapper.AccessControlMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
