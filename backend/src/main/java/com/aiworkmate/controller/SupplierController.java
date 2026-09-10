package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.SupplierDetailResponse;
import com.aiworkmate.dto.SupplierPageResponse;
import com.aiworkmate.dto.SupplierRequest;
import com.aiworkmate.dto.SupplierResponse;
import com.aiworkmate.dto.SupplierStatusRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.SupplierService;
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
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {
    private final SupplierService service;

    @GetMapping
    public Result<SupplierPageResponse> list(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(user.userId(), keyword, status, category, page, size));
    }

    @GetMapping("/{id}")
    public Result<SupplierDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable Long id) {
        return Result.ok(service.detail(user.userId(), id));
    }

    @PostMapping
    public Result<SupplierResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                           @Valid @RequestBody SupplierRequest request) {
        return Result.ok(service.create(user.userId(), request));
    }

    @PutMapping("/{id}")
    public Result<SupplierResponse> update(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable Long id,
                                           @Valid @RequestBody SupplierRequest request) {
        return Result.ok(service.update(user.userId(), id, request));
    }

    @PostMapping("/{id}/status")
    public Result<SupplierResponse> updateStatus(@AuthenticationPrincipal AuthenticatedUser user,
                                                 @PathVariable Long id,
                                                 @Valid @RequestBody SupplierStatusRequest request) {
        return Result.ok(service.updateStatus(user.userId(), id, request));
    }
}
