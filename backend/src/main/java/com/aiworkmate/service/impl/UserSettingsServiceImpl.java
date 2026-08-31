package com.aiworkmate.service.impl;

import com.aiworkmate.entity.UserSetting;
import com.aiworkmate.dto.ChatPreferencesRequest;
import com.aiworkmate.dto.ChatPreferencesResponse;
import com.aiworkmate.mapper.UserSettingMapper;
import com.aiworkmate.service.UserSettingsService;
import com.aiworkmate.service.model.AiModelCatalog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsServiceImpl implements UserSettingsService {

    static final String KEY_FORCE_PDF_OCR = "ocr.forcePdfOcr";
    static final String KEY_CHAT_MODEL = "chat.model";
    static final String KEY_CHAT_CONTEXT_ROUNDS = "chat.maxContextRounds";
    static final String KEY_CHAT_STREAM = "chat.stream";
    private static final int DEFAULT_CONTEXT_ROUNDS = 10;

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
        upsert(userId, KEY_FORCE_PDF_OCR, Boolean.toString(force));
        log.info("User OCR setting updated, userId={}, forcePdfOcr={}", userId, force);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatPreferencesResponse getChatPreferences(Long userId) {
        Map<String, UserSetting> settings = settings(userId);
        boolean initialized = settings.containsKey(KEY_CHAT_MODEL);
        String model = value(settings, KEY_CHAT_MODEL, AiModelCatalog.DEFAULT_MODEL);
        try {
            model = AiModelCatalog.normalize(model);
        } catch (RuntimeException ignored) {
            model = AiModelCatalog.DEFAULT_MODEL;
        }
        int rounds = parseRounds(value(settings, KEY_CHAT_CONTEXT_ROUNDS,
                Integer.toString(DEFAULT_CONTEXT_ROUNDS)));
        boolean stream = Boolean.parseBoolean(value(settings, KEY_CHAT_STREAM, "true"));
        boolean forceOcr = Boolean.parseBoolean(value(settings, KEY_FORCE_PDF_OCR, "false"));
        return new ChatPreferencesResponse(model, rounds, stream, forceOcr, initialized);
    }

    @Override
    @Transactional
    public ChatPreferencesResponse updateChatPreferences(Long userId, ChatPreferencesRequest request) {
        String model = AiModelCatalog.normalize(request.model());
        upsert(userId, KEY_CHAT_MODEL, model);
        upsert(userId, KEY_CHAT_CONTEXT_ROUNDS, Integer.toString(request.maxContextRounds()));
        upsert(userId, KEY_CHAT_STREAM, Boolean.toString(request.stream()));
        upsert(userId, KEY_FORCE_PDF_OCR, Boolean.toString(request.forcePdfOcr()));
        log.info("User chat preferences updated, userId={}, model={}, maxContextRounds={}, stream={}, forcePdfOcr={}",
                userId, model, request.maxContextRounds(), request.stream(), request.forcePdfOcr());
        return new ChatPreferencesResponse(model, request.maxContextRounds(), request.stream(),
                request.forcePdfOcr(), true);
    }

    private Map<String, UserSetting> settings(Long userId) {
        List<UserSetting> rows = userSettingMapper.selectList(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .in(UserSetting::getSettingKey, KEY_CHAT_MODEL, KEY_CHAT_CONTEXT_ROUNDS,
                        KEY_CHAT_STREAM, KEY_FORCE_PDF_OCR));
        return rows.stream().collect(Collectors.toMap(UserSetting::getSettingKey,
                Function.identity(), (left, right) -> right));
    }

    private String value(Map<String, UserSetting> settings, String key, String fallback) {
        UserSetting setting = settings.get(key);
        return setting == null || setting.getSettingValue() == null ? fallback : setting.getSettingValue();
    }

    private int parseRounds(String value) {
        try {
            int rounds = Integer.parseInt(value);
            return rounds >= 1 && rounds <= 20 ? rounds : DEFAULT_CONTEXT_ROUNDS;
        } catch (NumberFormatException ignored) {
            return DEFAULT_CONTEXT_ROUNDS;
        }
    }

    private void upsert(Long userId, String key, String value) {
        UserSetting setting = findSetting(userId, key);
        if (setting == null) {
            setting = new UserSetting();
            setting.setUserId(userId);
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            setting.setUpdatedAt(LocalDateTime.now());
            userSettingMapper.insert(setting);
        } else {
            setting.setSettingValue(value);
            setting.setUpdatedAt(LocalDateTime.now());
            userSettingMapper.updateById(setting);
        }
    }

    private UserSetting findSetting(Long userId, String key) {
        return userSettingMapper.selectOne(new LambdaQueryWrapper<UserSetting>()
                .eq(UserSetting::getUserId, userId)
                .eq(UserSetting::getSettingKey, key));
    }
}
