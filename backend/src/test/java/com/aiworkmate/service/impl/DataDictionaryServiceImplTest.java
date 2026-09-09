package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.DictionaryTypeRequest;
import com.aiworkmate.entity.DataDictionaryItem;
import com.aiworkmate.entity.DataDictionaryType;
import com.aiworkmate.mapper.DataDictionaryItemMapper;
import com.aiworkmate.mapper.DataDictionaryItemUsageMapper;
import com.aiworkmate.mapper.DataDictionaryTypeMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataDictionaryServiceImplTest {
    @Mock private DataDictionaryTypeMapper typeMapper;
    @Mock private DataDictionaryItemMapper itemMapper;
    @Mock private DataDictionaryItemUsageMapper usageMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;
    @InjectMocks private DataDictionaryServiceImpl service;

    @Test
    void shouldRequireDictionaryManagePermissionForMutation() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:dictionary")));

        assertThatThrownBy(() -> service.createType(1001L, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        verifyNoInteractions(typeMapper, itemMapper, usageMapper);
    }

    @Test
    void shouldCreateTenantScopedTypeAndAudit() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:dictionary", "dictionary:manage")));
        doAnswer(invocation -> { DataDictionaryType type = invocation.getArgument(0); type.setId(81L); return 1; })
                .when(typeMapper).insert(any(DataDictionaryType.class));
        when(itemMapper.selectCount(any())).thenReturn(0L);

        var response = service.createType(1001L, request(null));

        assertThat(response.id()).isEqualTo(81L);
        assertThat(response.code()).isEqualTo("EMPLOYEE_STATUS");
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(auditService).recordTransactional(9L, 1001L, "DICTIONARY_TYPE", "81", "CREATE", "SUCCESS", "EMPLOYEE_STATUS");
    }

    @Test
    void shouldRejectDeletingReferencedItem() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:dictionary", "dictionary:manage")));
        DataDictionaryItem item = new DataDictionaryItem();
        item.setId(32L); item.setTenantId(9L); item.setDictionaryTypeId(81L); item.setValue("ACTIVE"); item.setVersion(2); item.setDeleted(false);
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(usageMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.deleteItem(1001L, 81L, 32L, 2))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verifyNoInteractions(auditService);
    }

    @Test
    void shouldExposeOnlyActiveOptionsInsideActiveType() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of()));
        DataDictionaryType type = new DataDictionaryType(); type.setId(81L); type.setTenantId(9L); type.setStatus("ACTIVE"); type.setDeleted(false);
        DataDictionaryItem item = new DataDictionaryItem(); item.setValue("ACTIVE"); item.setLabel("在职"); item.setSortOrder(1);
        when(typeMapper.selectOne(any())).thenReturn(type);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));

        var result = service.activeOptions(1001L, "employee_status");

        assertThat(result).containsExactly(new com.aiworkmate.dto.DictionaryOptionResponse("ACTIVE", "在职", 1));
    }

    private DictionaryTypeRequest request(Integer version) {
        return new DictionaryTypeRequest("EMPLOYEE_STATUS", "员工状态", "员工档案状态", 10, version);
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(1001L, "admin@example.com", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 3L);
    }
}
