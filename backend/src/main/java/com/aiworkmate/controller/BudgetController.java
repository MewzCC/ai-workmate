package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.*;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {
    private final BudgetService service;
    @GetMapping public Result<BudgetPageResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) String status,
            @RequestParam(required=false) Integer fiscalYear, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size) {
        return Result.ok(service.list(user.userId(), keyword, status, fiscalYear, page, size));
    }
    @GetMapping("/options") public Result<BudgetOptionsResponse> options(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(service.options(user.userId()));
    }
    @GetMapping("/{id}") public Result<BudgetDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return Result.ok(service.detail(user.userId(), id));
    }
    @PostMapping public Result<BudgetResponse> create(@AuthenticationPrincipal AuthenticatedUser user, @Valid @RequestBody BudgetPlanRequest request) {
        return Result.ok(service.create(user.userId(), request));
    }
    @PutMapping("/{id}") public Result<BudgetResponse> update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, @Valid @RequestBody BudgetPlanRequest request) {
        return Result.ok(service.update(user.userId(), id, request));
    }
    @PostMapping("/{id}/status") public Result<BudgetResponse> status(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, @Valid @RequestBody BudgetStatusRequest request) {
        return Result.ok(service.updateStatus(user.userId(), id, request));
    }
    @PostMapping("/{id}/transactions") public Result<BudgetResponse> operate(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id, @Valid @RequestBody BudgetOperationRequest request) {
        return Result.ok(service.operate(user.userId(), id, request));
    }
}
