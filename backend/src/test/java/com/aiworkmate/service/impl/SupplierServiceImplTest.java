package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.SupplierRequest;
import com.aiworkmate.dto.SupplierStatusRequest;
import com.aiworkmate.entity.Supplier;
import com.aiworkmate.entity.SupplierStatusHistory;
import com.aiworkmate.mapper.SupplierMapper;
import com.aiworkmate.mapper.SupplierStatusHistoryMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {
    @Mock SupplierMapper supplierMapper;
    @Mock SupplierStatusHistoryMapper historyMapper;
    @Mock UserAccessService userAccessService;
    @Mock BusinessAuditService auditService;
    @InjectMocks SupplierServiceImpl service;

    @BeforeEach
    void initializeTableMetadata() {
        if (TableInfoHelper.getTableInfo(Supplier.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
            assistant.setCurrentNamespace(SupplierMapper.class.getName());
            TableInfoHelper.initTableInfo(assistant, Supplier.class);
        }
    }

    @Test
    void createRequiresSupplierManagementPermission() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(access(List.of("route:suppliers")));

        assertThatThrownBy(() -> service.create(10L, request(null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        verify(supplierMapper, never()).insert(any(Supplier.class));
    }

    @Test
    void createScopesSupplierToTenantAndStartsDraftHistory() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        org.mockito.Mockito.doAnswer(invocation -> {
            Supplier value = invocation.getArgument(0);
            value.setId(71L);
            return 1;
        }).when(supplierMapper).insert(any(Supplier.class));

        var response = service.create(10L, request(null));

        ArgumentCaptor<Supplier> supplier = ArgumentCaptor.forClass(Supplier.class);
        ArgumentCaptor<SupplierStatusHistory> history = ArgumentCaptor.forClass(SupplierStatusHistory.class);
        verify(supplierMapper).insert(supplier.capture());
        verify(historyMapper).insert(history.capture());
        assertThat(supplier.getValue().getTenantId()).isEqualTo(9L);
        assertThat(supplier.getValue().getSupplierCode()).isEqualTo("SUP-001");
        assertThat(supplier.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(history.getValue().getSupplierId()).isEqualTo(71L);
        assertThat(history.getValue().getToStatus()).isEqualTo("DRAFT");
        assertThat(response.allowedTransitions()).containsExactly("ACTIVE", "BLACKLISTED");
        verify(auditService).recordTransactional(9L, 10L, "SUPPLIER", "71", "CREATE", "SUCCESS", "SUP-001");
    }

    @Test
    void updateRejectsStaleVersionBeforeWriting() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        when(supplierMapper.selectOne(any())).thenReturn(supplier("ACTIVE", 4));

        assertThatThrownBy(() -> service.update(10L, 71L, request(3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        verify(supplierMapper, never()).update(any(), any());
    }

    @Test
    void statusChangeRejectsIllegalTransition() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        when(supplierMapper.selectOne(any())).thenReturn(supplier("DRAFT", 0));

        assertThatThrownBy(() -> service.updateStatus(10L, 71L,
                new SupplierStatusRequest("SUSPENDED", "暂停", 0)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_STATE_INVALID");
        verify(historyMapper, never()).insert(any(SupplierStatusHistory.class));
    }

    @Test
    void blacklistRequiresReason() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        when(supplierMapper.selectOne(any())).thenReturn(supplier("ACTIVE", 2));

        assertThatThrownBy(() -> service.updateStatus(10L, 71L,
                new SupplierStatusRequest("BLACKLISTED", " ", 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("REQUEST_INVALID");
        verify(supplierMapper, never()).update(any(), any());
    }

    @Test
    void validStatusChangeUsesOptimisticLockAndCreatesAuditTrail() {
        when(userAccessService.resolveActiveUser(10L)).thenReturn(manageAccess());
        Supplier before = supplier("ACTIVE", 2);
        Supplier after = supplier("SUSPENDED", 3);
        when(supplierMapper.selectOne(any())).thenReturn(before, after);
        when(supplierMapper.update(any(), any())).thenReturn(1);

        var response = service.updateStatus(10L, 71L,
                new SupplierStatusRequest("SUSPENDED", "等待资质复审", 2));

        ArgumentCaptor<SupplierStatusHistory> history = ArgumentCaptor.forClass(SupplierStatusHistory.class);
        verify(historyMapper).insert(history.capture());
        assertThat(history.getValue().getTenantId()).isEqualTo(9L);
        assertThat(history.getValue().getFromStatus()).isEqualTo("ACTIVE");
        assertThat(history.getValue().getToStatus()).isEqualTo("SUSPENDED");
        assertThat(history.getValue().getReason()).isEqualTo("等待资质复审");
        assertThat(response.status()).isEqualTo("SUSPENDED");
        assertThat(response.version()).isEqualTo(3);
        verify(auditService).recordTransactional(9L, 10L, "SUPPLIER", "71", "SET_STATUS", "SUCCESS",
                "SUP-001:ACTIVE->SUSPENDED");
    }

    private SupplierRequest request(Integer version) {
        return new SupplierRequest("sup-001", "示例供应商有限公司", "示例供应商",
                "91310000123456789X", "SERVICE", "PREFERRED", "张经理", "13800000000",
                "contact@example.com", "上海市", "验收后30天", "年度复审", version);
    }

    private Supplier supplier(String status, int version) {
        Supplier supplier = new Supplier();
        supplier.setId(71L); supplier.setTenantId(9L); supplier.setSupplierCode("SUP-001");
        supplier.setName("示例供应商有限公司"); supplier.setShortName("示例供应商");
        supplier.setCategory("SERVICE"); supplier.setSupplierLevel("PREFERRED"); supplier.setStatus(status);
        supplier.setVersion(version); supplier.setDeleted(false); supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        return supplier;
    }

    private ResolvedUserAccess manageAccess() {
        return access(List.of("route:suppliers", "supplier:manage"));
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(10L, "finance@example.com", 9L, "FINANCE_ADMIN",
                List.of("FINANCE_ADMIN"), permissions, List.of("TENANT"), 2L);
    }
}
