package com.aiworkmate.service.impl;

import com.aiworkmate.entity.UserSetting;
import com.aiworkmate.mapper.UserSettingMapper;
import com.aiworkmate.service.UserSettingsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsServiceImpl implements UserSettingsService {

    static final String KEY_FORCE_PDF_OCR = "ocr.forcePdfOcr";

    private final UserSettingMapper userSettingMapper;

    @Override
    @Transactional(readOnly = true)
    public boolean isForcePdfOcr(Long userId) {
        UserSetting setting = findSetting(userId, KEY_FORCE_PDF_OCR);
        return setting != null && "true".equalsIgnoreCase(setting.getSettingValue());
    }

    @Override
    @Transactional
    public void setForcePdfOcr(Long userId, boolean force) {
        UserSetting setting = findSetting(userId, KEY_FORCE_PDF_OCR);
        if (setting == null) {
            setting = new UserSetting();
            setting.setUserId(userId);
            setting.setSettingKey(KEY_FORCE_PDF_OCR);
            setting.setSettingValue(Boolean.toString(force));
            setting.setUpdatedAt(LocalDateTime.now());
            userSettingMapper.insert(setting);
        } else {
            setting.setSettingValue(Boolean.toString(force));
            setting.setUpdatedAt(LocalDateTime.now());
            userSettingMapper.updateById(setting);
        }
        log.info("User OCR setting updated, userId={}, forcePdfOcr={}", userId, force);
    }

    private UserSetting findSetting(Long userId, String key) {
        return userSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .eq(UserSetting::getSettingKey, key));
    }
}
