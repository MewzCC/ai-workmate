package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AuditRecordResponse;

import java.time.LocalDateTime;

public interface AuditQueryService {

    PageResponse<AuditRecordResponse> query(Long userId,
                                            Long actorUserId,
                                            String action,
                                            String resourceType,
                                            String result,
                                            LocalDateTime from,
                                            LocalDateTime to,
                                            int page,
                                            int size);
}
