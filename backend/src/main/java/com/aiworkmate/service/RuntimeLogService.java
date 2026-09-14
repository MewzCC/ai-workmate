package com.aiworkmate.service;

import com.aiworkmate.dto.RuntimeLogDetailResponse;
import com.aiworkmate.dto.RuntimeLogPageResponse;

import java.time.LocalDateTime;

public interface RuntimeLogService {
    RuntimeLogPageResponse query(Long userId, String source, String outcome, String keyword,
                                 LocalDateTime from, LocalDateTime to, int page, int size);

    RuntimeLogDetailResponse detail(Long userId, String source, Long id);
}
