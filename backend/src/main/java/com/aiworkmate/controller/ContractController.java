package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.ContractDetailResponse;
import com.aiworkmate.dto.ContractFulfillmentRequest;
import com.aiworkmate.dto.ContractOptionsResponse;
import com.aiworkmate.dto.ContractPageResponse;
import com.aiworkmate.dto.ContractPaymentRequest;
import com.aiworkmate.dto.ContractReminderRequest;
import com.aiworkmate.dto.ContractRequest;
import com.aiworkmate.dto.ContractResponse;
import com.aiworkmate.dto.ContractStatusRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService service;

    @GetMapping
    public Result<ContractPageResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contractType,
            @RequestParam(required = false) String expiryState,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(user.userId(), keyword, status, contractType, expiryState, page, size));
    }

    @GetMapping("/options")
    public Result<ContractOptionsResponse> options(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(service.options(user.userId()));
    }

    @GetMapping("/{id}")
    public Result<ContractDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id) {
        return Result.ok(service.detail(user.userId(), id));
    }

    @PostMapping
    public Result<ContractResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                            @Valid @RequestBody ContractRequest request) {
        return Result.ok(service.create(user.userId(), request));
    }

    @PutMapping("/{id}")
    public Result<ContractResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                            @PathVariable Long id,
                                            @Valid @RequestBody ContractRequest request) {
        return Result.ok(service.update(user.userId(), id, request));
    }

    @PostMapping("/{id}/status")
    public Result<ContractResponse> updateStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                                  @PathVariable Long id,
                                                  @Valid @RequestBody ContractStatusRequest request) {
        return Result.ok(service.updateStatus(user.userId(), id, request));
    }

    @PostMapping("/{id}/fulfillment")
    public Result<ContractResponse> updateFulfillment(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody ContractFulfillmentRequest request) {
        return Result.ok(service.updateFulfillment(user.userId(), id, request));
    }

    @PostMapping("/{id}/payments")
    public Result<ContractResponse> recordPayment(@AuthenticationPrincipal AuthenticatedUser user,
                                                   @PathVariable Long id,
                                                   @Valid @RequestBody ContractPaymentRequest request) {
        return Result.ok(service.recordPayment(user.userId(), id, request));
    }

    @PostMapping("/{id}/reminders")
    public Result<ContractResponse> remind(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable Long id,
                                           @Valid @RequestBody ContractReminderRequest request) {
        return Result.ok(service.remind(user.userId(), id, request));
    }
}
