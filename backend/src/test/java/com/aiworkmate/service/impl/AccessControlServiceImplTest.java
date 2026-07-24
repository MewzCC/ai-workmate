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
        when(accessControlMapper.countRole("EMPLOYEE")).thenReturn(1);
        when(accessControlMapper.selectUserRole(1L)).thenReturn("SUPER_ADMIN");
        when(accessControlMapper.countActiveSuperAdmins()).thenReturn(1);

        assertThatThrownBy(() -> accessControlService.assignRole(1L, 1L, "EMPLOYEE"))
                .isInstanceOf(BusinessException.class);

        verify(accessControlMapper, never()).updateUserRole(1L, "EMPLOYEE");
    }

    @Test
    void shouldAllowCreatingCustomRole() {
        when(accessControlMapper.countRole("SALES_MANAGER")).thenReturn(0);

        accessControlService.createRole(1L, "sales_manager", "销售主管", "管理销售团队");

        verify(accessControlMapper).insertRole("SALES_MANAGER", "销售主管", "管理销售团队");
        verify(accessControlMapper).insertAudit(
                1L, "CREATE_ROLE", "ROLE", "SALES_MANAGER", null, "销售主管");
    }
}
