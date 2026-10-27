package com.aiworkmate.service;

import com.aiworkmate.dto.AgentTaskDetailResponse;

public interface AgentTaskCommandService {
    AgentTaskDetailResponse cancel(Long userId, String taskId);
}
