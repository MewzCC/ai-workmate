package com.aiworkmate.service.impl;

import com.aiworkmate.entity.UserSetting;
import com.aiworkmate.dto.ChatPreferencesRequest;
import com.aiworkmate.mapper.UserSettingMapper;
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
