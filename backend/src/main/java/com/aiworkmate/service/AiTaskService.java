package com.aiworkmate.service;

import com.aiworkmate.dto.AiTaskExecuteRequest;
import com.aiworkmate.dto.AiTaskExecuteResponse;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.dto.AiTaskPlanResponse;
import com.aiworkmate.security.AuthenticatedUser;

public interface AiTaskService {
    AiTaskPlanResponse plan(AiTaskPlanRequest request, AuthenticatedUser user);

    AiTaskExecuteResponse execute(AiTaskExecuteRequest request, AuthenticatedUser user);
}
