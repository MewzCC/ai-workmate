package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.dto.TenantFeaturesRequest;
import com.aiworkmate.entity.TenantConfiguration;
import com.aiworkmate.entity.TenantConfigurationHistory;
import com.aiworkmate.mapper.TenantConfigurationHistoryMapper;
import com.aiworkmate.mapper.TenantConfigurationMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantConfigurationServiceImplTest {
    @Mock private TenantConfigurationMapper configurationMapper;
    @Mock private TenantConfigurationHistoryMapper historyMapper;
    @Mock private UserAccessService userAccessService;
    @Mock private BusinessAuditService auditService;
    @Mock private TenantConfigurationCache cache;
    @InjectMocks private TenantConfigurationServiceImpl service;

    @Test
    void shouldRequireManagePermissionForMutation() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:tenant-config")));

        assertThatThrownBy(() -> service.updateFeatures(1001L,
                new TenantFeaturesRequest(true, true, true, true, true, true, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
        verify(configurationMapper, never()).update(any(), any());
    }

    @Test
    void shouldReadOnlyCurrentTenantAndNeverExposeTenantIdentifier() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:tenant-config")));
        when(cache.get(9L)).thenReturn(Optional.empty());
        when(configurationMapper.selectOne(any())).thenReturn(configuration(9L, 2));

        var response = service.get(1001L);

        assertThat(response.tenantName()).isEqualTo("示例租户");
        assertThat(response.canManage()).isFalse();
        verify(cache).put(eq(9L), any(TenantConfiguration.class));
    }

    @Test
    void shouldUpdateWithOptimisticLockCreateHistoryAuditAndEvictCache() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:tenant-config", "tenant:config:manage")));
        TenantConfiguration before = configuration(9L, 2);
        TenantConfiguration after = configuration(9L, 3);
        after.setApprovalEnabled(false);
        when(configurationMapper.selectOne(any())).thenReturn(before, after);
        when(configurationMapper.update(any(), any())).thenReturn(1);
        doAnswer(invocation -> { TenantConfigurationHistory history = invocation.getArgument(0); history.setId(8L); return 1; })
                .when(historyMapper).insert(any(TenantConfigurationHistory.class));

        var response = service.updateFeatures(1001L,
                new TenantFeaturesRequest(false, true, true, true, true, true, 2));

        assertThat(response.version()).isEqualTo(3);
        assertThat(response.approvalEnabled()).isFalse();
        verify(historyMapper).insert(any(TenantConfigurationHistory.class));
        verify(auditService).recordTransactional(9L, 1001L, "TENANT_CONFIGURATION", "71",
                "UPDATE_FEATURES", "SUCCESS", "version=3");
        verify(cache).evict(9L);
    }

    @Test
    void shouldRejectStaleVersionBeforeWriting() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(access(List.of("route:tenant-config", "tenant:config:manage")));
        when(configurationMapper.selectOne(any())).thenReturn(configuration(9L, 4));

        assertThatThrownBy(() -> service.updateFeatures(1001L,
                new TenantFeaturesRequest(true, true, true, true, true, true, 3)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("VERSION_CONFLICT");
        verify(historyMapper, never()).insert(any(TenantConfigurationHistory.class));
        verify(cache, never()).evict(any());
    }

    @Test
    void responseContractMustNotContainSecretsOrTenantSelector() {
        List<String> fields = Stream.of(com.aiworkmate.dto.TenantConfigurationResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .toList();

        assertThat(fields).noneMatch(name -> name.contains("key") || name.contains("secret")
                || name.contains("connection") || name.equals("tenantid"));
    }

    private TenantConfiguration configuration(Long tenantId, int version) {
        TenantConfiguration value = new TenantConfiguration();
        value.setId(71L); value.setTenantId(tenantId); value.setTenantName("示例租户"); value.setTenantShortName("示例");
        value.setLocale("zh-CN"); value.setTimezone("Asia/Shanghai"); value.setFiscalYearStartMonth(1);
        value.setApprovalEnabled(true); value.setAttendanceEnabled(true); value.setAssetEnabled(true);
        value.setMeetingEnabled(true); value.setVisitorEnabled(true); value.setSealEnabled(true);
        value.setDefaultApprovalDays(3); value.setExpenseCurrency("CNY"); value.setPasswordMinLength(8);
        value.setSessionTimeoutMinutes(120); value.setVersion(version); value.setUpdatedAt(LocalDateTime.now());
        return value;
    }

    private ResolvedUserAccess access(List<String> permissions) {
        return new ResolvedUserAccess(1001L, "admin@example.com", 9L, "SYSTEM_ADMIN",
                List.of("SYSTEM_ADMIN"), permissions, List.of("TENANT"), 3L);
    }
}
