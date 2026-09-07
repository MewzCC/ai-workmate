package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.WorkbenchRecordRequest;
import com.aiworkmate.entity.WorkbenchRecord;
import com.aiworkmate.mapper.WorkbenchRecordMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkbenchRecordServiceImplTest {
    @Mock
    private WorkbenchRecordMapper mapper;
    @Mock
    private UserAccessService userAccessService;
    @Mock
    private BusinessAuditService auditService;
    @InjectMocks
    private WorkbenchRecordServiceImpl service;

    @Test
    void shouldRejectUnknownModuleBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.create(1001L, "unknown", request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("RESOURCE_NOT_FOUND");
        verifyNoInteractions(mapper, userAccessService);
    }

    @Test
    void shouldRequireRoutePermissionForRead() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of()));

        assertThatThrownBy(() -> service.list(1001L, "expense", null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("PERMISSION_DENIED");
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldRequireManagePermissionForCreate() {
        when(userAccessService.resolveActiveUser(1001L))
                .thenReturn(access(List.of("route:expense")));

        assertThatThrownBy(() -> service.create(1001L, "expense", request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo("PERMISSION_DENIED");
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldCreateTenantScopedRecordAndAudit() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of(
                "route:expense", "workbench:finance:manage")));
        doAnswer(invocation -> {
            WorkbenchRecord record = invocation.getArgument(0);
            record.setId(88L);
            return 1;
        }).when(mapper).insert(any(WorkbenchRecord.class));

        var response = service.create(1001L, "expense", request(null));

        assertThat(response.id()).isEqualTo(88L);
        assertThat(response.moduleKey()).isEqualTo("expense");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.canManage()).isTrue();
        verify(auditService).recordTransactional(9L, 1001L, "WORKBENCH_RECORD", "88",
                "CREATE_EXPENSE", "SUCCESS", "EXP-001");
    }

    private WorkbenchRecordRequest request(Integer version) {
        return new WorkbenchRecordRequest("EXP-001", "差旅报销", "差旅", "ACTIVE",
                new BigDecimal("800.00"), "财务组", "出差费用", version);
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(1001L, "admin@example.com", 9L, "FINANCE_ADMIN",
                List.of("FINANCE_ADMIN"), permissions, List.of("TENANT"), 3L);
    }
}
