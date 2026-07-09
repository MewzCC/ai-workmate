package com.aiworkmate.service;

import com.aiworkmate.dto.AiTaskExecuteRequest;
import com.aiworkmate.dto.AiTaskExecuteResponse;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.dto.AiTaskPlanResponse;

public interface AiTaskService {
    AiTaskPlanResponse plan(AiTaskPlanRequest request);

    AiTaskExecuteResponse execute(AiTaskExecuteRequest request);
}
