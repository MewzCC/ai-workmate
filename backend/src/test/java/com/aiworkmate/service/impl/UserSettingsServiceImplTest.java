package com.aiworkmate.service.impl;

import com.aiworkmate.entity.UserSetting;
import com.aiworkmate.dto.ChatPreferencesRequest;
import com.aiworkmate.mapper.UserSettingMapper;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.common.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceImplTest {

    @Mock
    private UserSettingMapper userSettingMapper;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private BusinessAuditService auditService;

    @InjectMocks
    private UserSettingsServiceImpl settingsService;

    @Test
    void shouldReturnFalseWhenSettingAbsent() {
        when(userSettingMapper.selectOne(any())).thenReturn(null);

        assertThat(settingsService.isForcePdfOcr(1001L)).isFalse();
    }

    @Test
    void shouldReturnTrueWhenSettingIsTrue() {
        when(userSettingMapper.selectOne(any())).thenReturn(setting("true"));

        assertThat(settingsService.isForcePdfOcr(1001L)).isTrue();
    }

    @Test
    void shouldInsertSettingWhenAbsent() {
        when(userSettingMapper.selectOne(any())).thenReturn(null);

        settingsService.setForcePdfOcr(1001L, true);

        verify(userSettingMapper).insert(any(UserSetting.class));
    }

    @Test
    void shouldUpdateSettingWhenPresent() {
        UserSetting existing = setting("false");
        when(userSettingMapper.selectOne(any())).thenReturn(existing);

        settingsService.setForcePdfOcr(1001L, true);

        assertThat(existing.getSettingValue()).isEqualTo("true");
        verify(userSettingMapper).updateById(existing);
    }

    @Test
    void chatPreferencesReturnServerDefaultsWhenNotInitialized() {
        when(userSettingMapper.selectList(any())).thenReturn(java.util.List.of());

        var response = settingsService.getChatPreferences(1001L);

        assertThat(response.model()).isEqualTo("deepseek-v4-flash");
        assertThat(response.maxContextRounds()).isEqualTo(10);
        assertThat(response.stream()).isTrue();
        assertThat(response.forcePdfOcr()).isFalse();
        assertThat(response.initialized()).isFalse();
    }

    @Test
    void chatPreferencesReadUnifiedServerValues() {
        when(userSettingMapper.selectList(any())).thenReturn(java.util.List.of(
                setting("chat.model", "deepseek-v4-pro"),
                setting("chat.maxContextRounds", "16"),
                setting("chat.stream", "false"),
                setting("ocr.forcePdfOcr", "true")));

        var response = settingsService.getChatPreferences(1001L);

        assertThat(response.model()).isEqualTo("deepseek-v4-pro");
        assertThat(response.maxContextRounds()).isEqualTo(16);
        assertThat(response.stream()).isFalse();
        assertThat(response.forcePdfOcr()).isTrue();
        assertThat(response.initialized()).isTrue();
    }

    @Test
    void updateChatPreferencesPersistsAllFourFields() {
        when(userSettingMapper.selectOne(any())).thenReturn(null);

        var response = settingsService.updateChatPreferences(1001L,
                new ChatPreferencesRequest("deepseek-v4-pro", 12, false, true));

        assertThat(response.initialized()).isTrue();
        assertThat(response.model()).isEqualTo("deepseek-v4-pro");
        verify(userSettingMapper, times(4)).insert(any(UserSetting.class));
    }

    @Test
    void agentUpdateRechecksLivePermissionAndAuditsInTheSameTransaction() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(new ResolvedUserAccess(
                1001L, "alice", 7L, "SYSTEM_ADMIN", java.util.List.of("SYSTEM_ADMIN"),
                java.util.List.of("settings:self:update"), java.util.List.of("SELF"), 3L));
        when(userSettingMapper.selectOne(any())).thenReturn(null);

        var response = settingsService.updateChatPreferencesByAgent(1001L,
                new ChatPreferencesRequest("deepseek-v4-pro", 8, true, false));

        assertThat(response.model()).isEqualTo("deepseek-v4-pro");
        verify(userSettingMapper, times(4)).insert(any(UserSetting.class));
        verify(auditService).recordTransactional(7L, 1001L, "USER_SETTINGS", "1001",
                "UPDATE_CHAT_PREFERENCES", "SUCCESS",
                "Updated personal chat and OCR preferences through Agent");
    }

    @Test
    void agentUpdateFailsClosedWhenLivePermissionWasRevoked() {
        when(userAccessService.resolveActiveUser(1001L)).thenReturn(new ResolvedUserAccess(
                1001L, "alice", 7L, "EMPLOYEE", java.util.List.of("EMPLOYEE"),
                java.util.List.of(), java.util.List.of("SELF"), 4L));

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                settingsService.updateChatPreferencesByAgent(1001L,
                        new ChatPreferencesRequest("deepseek-v4-flash", 10, true, false)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void dashboardMetricsReadOrderedDistinctCodes() {
        when(userSettingMapper.selectOne(any())).thenReturn(
                setting("dashboard.metricCodes", "UNREAD_MESSAGES,PENDING_TODOS,UNREAD_MESSAGES"));

        assertThat(settingsService.getDashboardMetricCodes(1001L))
                .containsExactly("UNREAD_MESSAGES", "PENDING_TODOS");
    }

    @Test
    void dashboardMetricsPersistOrderedCodesInExistingUserSetting() {
        UserSetting existing = setting("dashboard.metricCodes", "PENDING_TODOS");
        when(userSettingMapper.selectOne(any())).thenReturn(existing);

        settingsService.setDashboardMetricCodes(1001L,
                java.util.List.of("MY_APPLICATIONS", "PENDING_TODOS"));

        assertThat(existing.getSettingValue()).isEqualTo("MY_APPLICATIONS,PENDING_TODOS");
        verify(userSettingMapper).updateById(existing);
    }

    @Test
    void observabilityChartsUseExistingUserSettingWithoutNewTable() {
        UserSetting existing = setting("observability.charts", "[]");
        when(userSettingMapper.selectOne(any())).thenReturn(existing);
        settingsService.setObservabilityChartConfig(1001L, "[{\"id\":\"volume\"}]");
        assertThat(settingsService.getObservabilityChartConfig(1001L))
                .isEqualTo("[{\"id\":\"volume\"}]");
        verify(userSettingMapper).updateById(existing);
    }

    @Test
    void observabilityVisualThresholdsUseExistingUserSettingWithoutNewTable() {
        UserSetting existing = setting("observability.thresholds", "{}");
        when(userSettingMapper.selectOne(any())).thenReturn(existing);
        settingsService.setObservabilityThresholdConfig(1001L, "{\"failedCount\":5}");
        assertThat(settingsService.getObservabilityThresholdConfig(1001L))
                .isEqualTo("{\"failedCount\":5}");
        verify(userSettingMapper).updateById(existing);
    }

    private UserSetting setting(String value) {
        return setting("ocr.forcePdfOcr", value);
    }

    private UserSetting setting(String key, String value) {
        UserSetting setting = new UserSetting();
        setting.setId(1L);
        setting.setUserId(1001L);
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
