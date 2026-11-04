package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.impl.AgentChatResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class AgentChatResultController {
    private final AgentChatResultService service;
    private final com.aiworkmate.agent.task.AgentTaskApiService taskApi;

    @org.springframework.web.bind.annotation.GetMapping("/{conversationId}/agent-task")
    public Result<com.aiworkmate.dto.AgentTaskDetailResponse> latest(@PathVariable Long conversationId,
                                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        var task = service.latestTask(user, conversationId);
        return Result.ok(task == null ? null : taskApi.detail(user, task.getTaskNo()));
    }

    @PostMapping("/{conversationId}/agent-results/{taskId}")
    public Result<String> append(@PathVariable Long conversationId, @PathVariable String taskId,
                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(service.appendResult(user, conversationId, taskId));
    }
}
