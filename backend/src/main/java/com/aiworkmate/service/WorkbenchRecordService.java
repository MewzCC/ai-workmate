package com.aiworkmate.service;

import com.aiworkmate.dto.WorkbenchPageResponse;
import com.aiworkmate.dto.WorkbenchRecordRequest;
import com.aiworkmate.dto.WorkbenchRecordResponse;

public interface WorkbenchRecordService {
    WorkbenchPageResponse list(Long userId, String moduleKey, String keyword,
                               String status, int page, int size);

    WorkbenchRecordResponse create(Long userId, String moduleKey, WorkbenchRecordRequest request);

    WorkbenchRecordResponse update(Long userId, String moduleKey, Long id, WorkbenchRecordRequest request);

    void delete(Long userId, String moduleKey, Long id, Integer version);
}
