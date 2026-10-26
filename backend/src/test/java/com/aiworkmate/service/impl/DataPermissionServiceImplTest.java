package com.aiworkmate.service.impl;

import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.DataPermissionUserExceptionResponse;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.DataPermissionMapper;
import com.aiworkmate.service.AccessControlService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPermissionServiceImplTest {
    @Mock DataPermissionMapper mapper;
    @Mock AccessControlMapper accessMapper;
    @Mock AccessControlService accessControlService;
    @Mock UserAccessService userAccessService;
    @Mock BusinessAuditService auditService;
    DataPermissionServiceImpl service;

    @BeforeEach void setUp(){service=new DataPermissionServiceImpl(mapper,accessMapper,accessControlService,userAccessService,auditService);}

    @ParameterizedTest @ValueSource(strings = {"SELF", "DEPARTMENT", "DEPARTMENT_TREE", "DEPARTMENT_AND_CHILDREN"})
    void anActiveUnassignedEmployeeCanReadOnlySelfWithoutAcquiringOtherUnassignedUsers(String scope) {
        when(mapper.selectActiveUser(9L,11L)).thenReturn(new DataPermissionMapper.ActiveUserDepartment(11L,null));
        when(mapper.selectEffectiveScopes(9L,11L)).thenReturn(List.of(scope));
        when(mapper.selectUserExceptions(9L)).thenReturn(List.of());
        when(accessMapper.selectUsers(9L)).thenReturn(List.of(user(11L,null),user(12L,null),user(13L,100L)));
        var result=service.resolve(9L,11L);
        assertThat(result.departmentIds()).isEmpty();
        assertThat(result.visibleUserIds()).containsExactly(11L);
    }

    @ParameterizedTest @ValueSource(strings = {"DEPARTMENT_AND_CHILDREN", "DEPARTMENT_TREE"})
    void resolvesDepartmentAndChildrenIntoVisibleUsers(String scope){
        when(mapper.selectActiveUser(9L,11L)).thenReturn(new DataPermissionMapper.ActiveUserDepartment(11L,100L));
        when(mapper.selectEffectiveScopes(9L,11L)).thenReturn(List.of(scope));
        when(mapper.selectUserExceptions(9L)).thenReturn(List.of());
        when(accessMapper.selectDepartments(9L)).thenReturn(List.of(
                new DepartmentResponse(100L,"ROOT","Root",null,null,1),
                new DepartmentResponse(101L,"CHILD","Child",100L,null,1),
                new DepartmentResponse(102L,"OTHER","Other",null,null,1)));
        when(accessMapper.selectUsers(9L)).thenReturn(List.of(user(11L,100L),user(12L,101L),user(13L,102L)));

        var result=service.resolve(9L,11L);

        assertThat(result.departmentIds()).containsExactlyInAnyOrder(100L,101L);
        assertThat(result.visibleUserIds()).containsExactlyInAnyOrder(11L,12L);
        assertThat(result.source()).isEqualTo("ROLE");
    }

    @Test void userExceptionOverridesRoleAndUsesCustomDepartments(){
        when(mapper.selectActiveUser(9L,11L)).thenReturn(new DataPermissionMapper.ActiveUserDepartment(11L,100L));
        when(mapper.selectEffectiveScopes(9L,11L)).thenReturn(List.of("CUSTOM_DEPARTMENTS"));
        when(mapper.selectEffectiveCustomDepartments(9L,11L)).thenReturn(List.of(102L));
        when(mapper.selectUserExceptions(9L)).thenReturn(List.of(new DataPermissionUserExceptionResponse(11L,7L)));
        when(accessMapper.selectUsers(9L)).thenReturn(List.of(user(11L,100L),user(13L,102L)));

        var result=service.resolve(9L,11L);

        assertThat(result.source()).isEqualTo("USER_EXCEPTION");
        assertThat(result.visibleUserIds()).containsExactlyInAnyOrder(11L,13L);
    }

    @Test void missingInactiveOrOtherTenantUserIsRejectedBeforeResolvingScopes() {
        assertThatThrownBy(() -> service.resolve(9L,11L)).isInstanceOf(com.aiworkmate.common.BusinessException.class);
        org.mockito.Mockito.verify(mapper,org.mockito.Mockito.never()).selectEffectiveScopes(9L,11L);
    }

    @Test void identityLookupErrorsNeverBecomeSuccessfulEmptyScope() {
        when(mapper.selectActiveUser(9L,11L)).thenThrow(new IllegalStateException("unavailable"));
        assertThatThrownBy(() -> service.resolve(9L,11L)).isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verifyNoInteractions(accessMapper);
    }

    private AccessUserRow user(long id,Long departmentId){return new AccessUserRow(id,"U"+id,"u@example.com","EMPLOYEE",1,9L,departmentId,1L,null,1L, LocalDateTime.now(),null);}
}
