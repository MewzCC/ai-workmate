package com.aiworkmate.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(
        Long id,
        Long messageId,
        String type,
        String name,
        Long size,
        String mimeType,
        String contentUrl,
        boolean parsed,
        /** 附件是否已提取文本；图片表示 OCR 识别成功 */
        boolean hasText,
        LocalDateTime createdAt
) {
}
