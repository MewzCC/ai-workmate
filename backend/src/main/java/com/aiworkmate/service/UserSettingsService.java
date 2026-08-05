package com.aiworkmate.service;

public interface UserSettingsService {

    /** 是否强制所有 PDF 走 OCR（默认 false：仅扫描版 PDF 自动 OCR） */
    boolean isForcePdfOcr(Long userId);

    void setForcePdfOcr(Long userId, boolean force);
}
