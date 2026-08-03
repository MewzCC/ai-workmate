package com.aiworkmate.dto;

import java.time.LocalDateTime;

/**
 * 站内通知返回体。
 */
public record NotificationResponse(
        Long id,
        String type,
        String title,
        String content,
        String bizType,
        Long bizId,
        boolean read,
        LocalDateTime createdAt
) {
}
