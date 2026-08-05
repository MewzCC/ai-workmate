package com.aiworkmate.service.impl;

import com.aiworkmate.entity.UserSetting;
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

    private UserSetting setting(String value) {
        UserSetting setting = new UserSetting();
        setting.setId(1L);
        setting.setUserId(1001L);
        setting.setSettingKey("ocr.forcePdfOcr");
        setting.setSettingValue(value);
        return setting;
    }
}
