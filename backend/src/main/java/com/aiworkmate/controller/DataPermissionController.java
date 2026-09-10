package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.*;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.DataPermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/data-permissions")
@PreAuthorize("hasAuthority('data-scope:manage')")
@RequiredArgsConstructor
public class DataPermissionController {
    private final DataPermissionService service;
    @GetMapping public Result<DataPermissionOverviewResponse> overview(@AuthenticationPrincipal AuthenticatedUser actor){return Result.ok(service.overview(actor.userId()));}
    @PostMapping("/policies") public Result<DataPermissionPolicyResponse> create(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody SaveDataPermissionPolicyRequest request){return Result.ok(service.create(actor.userId(),request));}
    @PutMapping("/policies/{id}") public Result<DataPermissionPolicyResponse> update(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@Valid @RequestBody SaveDataPermissionPolicyRequest request){return Result.ok(service.update(actor.userId(),id,request));}
    @DeleteMapping("/policies/{id}") public Result<Void> delete(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@RequestParam long version){service.delete(actor.userId(),id,version);return Result.ok();}
    @PutMapping("/roles/{roleCode}") public Result<Void> bindRole(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable String roleCode,@Valid @RequestBody BindDataPermissionPolicyRequest request){service.bindRole(actor.userId(),roleCode,request.policyId());return Result.ok();}
    @PutMapping("/users/{userId}/exception") public Result<Void> bindUser(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long userId,@Valid @RequestBody BindDataPermissionPolicyRequest request){service.bindUserException(actor.userId(),userId,request.policyId());return Result.ok();}
    @DeleteMapping("/users/{userId}/exception") public Result<Void> clearUser(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long userId){service.clearUserException(actor.userId(),userId);return Result.ok();}
    @GetMapping("/preview/{userId}") public Result<DataPermissionPreviewResponse> preview(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long userId){return Result.ok(service.preview(actor.userId(),userId));}
}
