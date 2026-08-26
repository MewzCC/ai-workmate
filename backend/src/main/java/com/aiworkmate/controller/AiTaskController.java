package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AiTaskExecuteRequest;
import com.aiworkmate.dto.AiTaskExecuteResponse;
import com.aiworkmate.dto.AiTaskPlanRequest;
import com.aiworkmate.dto.AiTaskPlanResponse;
import com.aiworkmate.service.AiTaskService;
import com.aiworkmate.agent.task.AgentTaskApiService;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AgentConfirmationTokenRequest;
import com.aiworkmate.dto.AgentConfirmationTokenResponse;
import com.aiworkmate.dto.AgentTaskDetailResponse;
import com.aiworkmate.dto.AgentTaskSummaryResponse;
import com.aiworkmate.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/tasks")
@RequiredArgsConstructor
@Slf4j
public class AiTaskController {

    private final AiTaskService aiTaskService;
    private final AgentTaskApiService agentTaskApiService;

    @PostMapping("/plan")
    public Result<AiTaskPlanResponse> plan(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                           @Valid @RequestBody AiTaskPlanRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(aiTaskService.plan(request, idempotencyKey, user));
    }

    @PostMapping("/{taskId}/execute")
    public ResponseEntity<Result<AiTaskExecuteResponse>> execute(
            @PathVariable String taskId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AiTaskExecuteRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.accepted().body(Result.ok(aiTaskService.execute(taskId, request, idempotencyKey, user)));
    }

    @GetMapping
    public Result<PageResponse<AgentTaskSummaryResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(agentTaskApiService.list(user, status, from, to, page, size));
    }

    @GetMapping("/{taskId}")
    public Result<AgentTaskDetailResponse> detail(@PathVariable String taskId,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(agentTaskApiService.detail(user, taskId));
    }

    @PostMapping("/{taskId}/cancel")
    public Result<AgentTaskDetailResponse> cancel(@PathVariable String taskId,
                                                   @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(agentTaskApiService.cancel(user, taskId));
    }

    @PostMapping("/{taskId}/confirmation-token")
    public Result<AgentConfirmationTokenResponse> confirmationToken(
            @PathVariable String taskId,
            @Valid @RequestBody AgentConfirmationTokenRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(agentTaskApiService.issueConfirmation(user, taskId, request));
    }

    @GetMapping(value = "/{taskId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String taskId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             @AuthenticationPrincipal AuthenticatedUser user) {
        try {
            return agentTaskApiService.events(user, taskId, parseLastEventId(lastEventId));
        } catch (BusinessException exception) {
            return errorEmitter(exception.getCode(), exception.getErrorCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.warn("Agent task SSE setup failed");
            return errorEmitter(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getErrorCode(),
                    com.aiworkmate.common.MessageUtils.resolve(ErrorCode.SYSTEM_ERROR.getMessageKey()));
        }
    }

    private long parseLastEventId(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
    }

    private SseEmitter errorEmitter(int code, String errorCode, String message) {
        SseEmitter emitter = new SseEmitter(30_000L);
        try {
            emitter.send(SseEmitter.event().name("task-failed")
                    .data(Map.of("code", code, "errorCode", errorCode, "message", message)));
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }
}
