package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.DictionaryTypeRequest;
import com.aiworkmate.dto.DictionaryTypeAgentUpdateRequest;
import com.aiworkmate.dto.DictionaryItemAgentCreateRequest;
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
    void shouldCreateAgentTypeThroughTheSameTenantScopedTransaction() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:dictionary", "dictionary:manage")));
        doAnswer(invocation -> { DataDictionaryType type = invocation.getArgument(0); type.setId(91L); return 1; })
                .when(typeMapper).insert(any(DataDictionaryType.class));
        when(itemMapper.selectCount(any())).thenReturn(0L);

        var response = service.createTypeAgent(1001L,
                new DictionaryTypeRequest("PROJECT_STAGE", "项目阶段", "项目阶段字典", 20, null));

        assertThat(response.id()).isEqualTo(91L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(auditService).recordTransactional(9L, 1001L, "DICTIONARY_TYPE", "91", "CREATE", "SUCCESS", "PROJECT_STAGE");
    }

    @Test
    void shouldRejectInvalidAgentTypeBeforeResolvingActorOrWriting() {
        assertThatThrownBy(() -> service.createTypeAgent(1001L,
                new DictionaryTypeRequest("bad-code", "项目阶段", null, 0, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        verifyNoInteractions(userAccessService, typeMapper, itemMapper, usageMapper, auditService);
    }

    @Test
    void shouldUpdateAgentTypeByImmutableCodeWithOptimisticVersion() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(
                access(List.of("route:dictionary", "dictionary:manage")));
        DataDictionaryType existing = dictionaryType("PROJECT_STAGE", "项目阶段", "原描述", 20, 2);
        DataDictionaryType updated = dictionaryType("PROJECT_STAGE", "项目阶段新版", null, 30, 3);
        when(typeMapper.selectOne(any())).thenReturn(existing, updated);
        when(typeMapper.update(any(), any())).thenReturn(1);
        when(itemMapper.selectCount(any())).thenReturn(0L);

        var response = service.updateTypeAgent(1001L, "PROJECT_STAGE",
                new DictionaryTypeAgentUpdateRequest(2, "项目阶段新版", "", 30));

        assertThat(response.name()).isEqualTo("项目阶段新版");
        assertThat(response.description()).isNull();
        assertThat(response.version()).isEqualTo(3);
        verify(auditService).recordTransactional(
                9L, 1001L, "DICTIONARY_TYPE", "81", "UPDATE", "SUCCESS", "PROJECT_STAGE");
    }

    @Test
    void shouldRejectAgentTypeUpdateWithoutChangeBeforeResolvingActor() {
        assertThatThrownBy(() -> service.updateTypeAgent(1001L, "PROJECT_STAGE",
                new DictionaryTypeAgentUpdateRequest(2, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        verifyNoInteractions(userAccessService, typeMapper, itemMapper, usageMapper, auditService);
    }

    @Test
    void shouldCreateAgentItemInsideActiveTypeAndAudit() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(
                access(List.of("route:dictionary", "dictionary:manage")));
        when(typeMapper.selectOne(any())).thenReturn(
                dictionaryType("PROJECT_STAGE", "项目阶段", null, 20, 1));
        doAnswer(invocation -> { DataDictionaryItem item = invocation.getArgument(0); item.setId(101L); return 1; })
                .when(itemMapper).insert(any(DataDictionaryItem.class));
        when(usageMapper.selectCount(any())).thenReturn(0L);

        var response = service.createItemAgent(1001L, "PROJECT_STAGE",
                new DictionaryItemAgentCreateRequest("IN_PROGRESS", "进行中", "处理中", 10));

        assertThat(response.id()).isEqualTo(101L);
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.usageCount()).isZero();
        verify(auditService).recordTransactional(
                9L, 1001L, "DICTIONARY_ITEM", "101", "CREATE", "SUCCESS", "IN_PROGRESS");
    }

    @Test
    void shouldRejectInvalidAgentItemBeforeResolvingActor() {
        assertThatThrownBy(() -> service.createItemAgent(1001L, "PROJECT_STAGE",
                new DictionaryItemAgentCreateRequest("bad value", "进行中", null, 10)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        verifyNoInteractions(userAccessService, typeMapper, itemMapper, usageMapper, auditService);
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

    private DataDictionaryType dictionaryType(
            String code, String name, String description, int sortOrder, int version) {
        DataDictionaryType type = new DataDictionaryType();
        type.setId(81L); type.setTenantId(9L); type.setCode(code); type.setName(name);
        type.setDescription(description); type.setSortOrder(sortOrder); type.setStatus("ACTIVE");
        type.setVersion(version); type.setDeleted(false);
        return type;
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(1001L, "admin@example.com", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 3L);
    }
}
