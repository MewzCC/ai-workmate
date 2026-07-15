package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AiTaskExecuteRequest;
import com.aiworkmate.dto.AiTaskExecuteResponse;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.dto.AiTaskPlanResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AiTaskServiceImpl implements AiTaskService {

    @Override
    public AiTaskPlanResponse plan(AiTaskPlanRequest request, AuthenticatedUser user) {
        log.info("AI task plan rejected because no real tool is registered: userId={}, pageId={}",
                user.userId(), request.getPageId());
        throw new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE);
    }

    @Override
    public AiTaskExecuteResponse execute(AiTaskExecuteRequest request, AuthenticatedUser user) {
        if (!request.isConfirm()) {
            throw new BusinessException(ErrorCode.AI_TASK_CONFIRMATION_REQUIRED);
        }
        log.info("AI task execution rejected because no real tool is registered: userId={}, taskId={}",
                user.userId(), request.getTaskId());
        throw new BusinessException(ErrorCode.AI_TASK_CAPABILITY_UNAVAILABLE);
    }
}
