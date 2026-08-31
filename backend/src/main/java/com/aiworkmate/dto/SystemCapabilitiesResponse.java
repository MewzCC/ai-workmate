package com.aiworkmate.dto;

import java.time.Instant;

public record SystemCapabilitiesResponse(
        Instant checkedAt,
        SystemCapabilityStatusResponse ai,
        SystemCapabilityStatusResponse embedding,
        SystemCapabilityStatusResponse ocr,
        SystemCapabilityStatusResponse minio,
        SystemCapabilityStatusResponse redis
) {
}
