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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test void resolvesDepartmentAndChildrenIntoVisibleUsers(){
        when(mapper.selectActiveUserDepartment(9L,11L)).thenReturn(100L);
        when(mapper.selectEffectiveScopes(9L,11L)).thenReturn(List.of("DEPARTMENT_AND_CHILDREN"));
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
        when(mapper.selectActiveUserDepartment(9L,11L)).thenReturn(100L);
        when(mapper.selectEffectiveScopes(9L,11L)).thenReturn(List.of("CUSTOM_DEPARTMENTS"));
        when(mapper.selectEffectiveCustomDepartments(9L,11L)).thenReturn(List.of(102L));
        when(mapper.selectUserExceptions(9L)).thenReturn(List.of(new DataPermissionUserExceptionResponse(11L,7L)));
        when(accessMapper.selectUsers(9L)).thenReturn(List.of(user(11L,100L),user(13L,102L)));

        var result=service.resolve(9L,11L);

        assertThat(result.source()).isEqualTo("USER_EXCEPTION");
        assertThat(result.visibleUserIds()).containsExactlyInAnyOrder(11L,13L);
    }

    private AccessUserRow user(long id,long departmentId){return new AccessUserRow(id,"U"+id,"u@example.com","EMPLOYEE",1,9L,departmentId,1L,null,1L, LocalDateTime.now(),null);}
}
