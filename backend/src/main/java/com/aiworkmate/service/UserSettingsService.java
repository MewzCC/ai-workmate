package com.aiworkmate.service;

import com.aiworkmate.dto.ChatPreferencesRequest;
import com.aiworkmate.dto.ChatPreferencesResponse;

import java.util.List;

public interface UserSettingsService {

    /** 是否强制所有 PDF 走 OCR（默认 false：仅扫描版 PDF 自动 OCR） */
    boolean isForcePdfOcr(Long userId);

    void setForcePdfOcr(Long userId, boolean force);

    ChatPreferencesResponse getChatPreferences(Long userId);

    ChatPreferencesResponse updateChatPreferences(Long userId, ChatPreferencesRequest request);

    List<String> getDashboardMetricCodes(Long userId);

    void setDashboardMetricCodes(Long userId, List<String> metricCodes);
}
