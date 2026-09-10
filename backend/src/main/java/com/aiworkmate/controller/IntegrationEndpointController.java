package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.*;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.IntegrationEndpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController @RequestMapping("/api/integration/endpoints") @RequiredArgsConstructor
public class IntegrationEndpointController {
 private final IntegrationEndpointService service;
 @GetMapping public Result<IntegrationPageResponse> list(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(required=false)String keyword,@RequestParam(required=false)String status,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int size){return Result.ok(service.list(user.userId(),keyword,status,page,size));}
 @GetMapping("/options") public Result<IntegrationOptionsResponse> options(@AuthenticationPrincipal AuthenticatedUser user){return Result.ok(service.options(user.userId()));}
 @GetMapping("/{id}") public Result<IntegrationDetailResponse> detail(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id){return Result.ok(service.detail(user.userId(),id));}
 @PostMapping public Result<IntegrationEndpointResponse> create(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody IntegrationEndpointRequest request){return Result.ok(service.create(user.userId(),request));}
 @PutMapping("/{id}") public Result<IntegrationEndpointResponse> update(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@Valid @RequestBody IntegrationEndpointRequest request){return Result.ok(service.update(user.userId(),id,request));}
 @PostMapping("/{id}/status") public Result<IntegrationEndpointResponse> status(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@Valid @RequestBody IntegrationStatusRequest request){return Result.ok(service.updateStatus(user.userId(),id,request));}
 @PostMapping("/{id}/execute") public Result<IntegrationInvocationResponse> execute(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long id,@RequestParam Integer version){return Result.ok(service.execute(user.userId(),id,version));}
}
