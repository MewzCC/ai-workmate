package com.aiworkmate.service;

import com.aiworkmate.dto.TodoQueryResult;
import com.aiworkmate.dto.UnifiedTodoResponse;
import java.time.LocalDateTime;

public interface TodoQueryService {
    TodoQueryResult mine(Long userId,Long expectedTenantId,String businessType,String status,
            LocalDateTime from,LocalDateTime to,int page,int size);
    UnifiedTodoResponse summary(Long userId,Long expectedTenantId,Long taskId);
}
