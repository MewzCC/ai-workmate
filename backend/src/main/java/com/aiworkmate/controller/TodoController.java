package com.aiworkmate.controller;

import com.aiworkmate.dto.TodoQueryResult;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.LeaveApplicationResponse;
import com.aiworkmate.dto.UnifiedTodoResponse;
import com.aiworkmate.service.TodoQueryService;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.LeaveWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final LeaveWorkflowService service;
    private final TodoQueryService queries;

    @GetMapping
    public Result<TodoQueryResult> todos(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(queries.mine(user.userId(), null, businessType, status, from, to, page, size));
    }

    @GetMapping("/{id}/summary")
    public Result<UnifiedTodoResponse> summary(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id) {
        return Result.ok(queries.summary(user.userId(),null,id));
    }

    @GetMapping("/{id}")
    public Result<LeaveApplicationResponse> detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        return Result.ok(service.todoDetail(user.userId(), id));
    }
}
