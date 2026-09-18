package com.aiworkmate.service;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AgentTaskSummaryResponse;

import java.time.LocalDateTime;

public interface AgentTaskQueryService {
    PageResponse<AgentTaskSummaryResponse> mine(Long userId, String status, LocalDateTime from,
                                                LocalDateTime to, int page, int size);
}
