package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AiTaskExecuteRequest;
import com.aiworkmate.dto.AiTaskExecuteResponse;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.dto.AiTaskPlanResponse;
import com.aiworkmate.service.AiTaskService;
import com.aiworkmate.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/ai/tasks")
@RequiredArgsConstructor
public class AiTaskController {

    private final AiTaskService aiTaskService;

    @PostMapping("/plan")
    public Result<AiTaskPlanResponse> plan(@Valid @RequestBody AiTaskPlanRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(aiTaskService.plan(request, user));
    }

    @PostMapping("/execute")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'SYSTEM_ADMIN', 'PROCESS_ADMIN', 'FINANCE_ADMIN')")
    public Result<AiTaskExecuteResponse> execute(@Valid @RequestBody AiTaskExecuteRequest request,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(aiTaskService.execute(request, user));
    }
}
