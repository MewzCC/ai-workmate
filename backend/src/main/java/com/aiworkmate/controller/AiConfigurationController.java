package com.aiworkmate.controller;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.*;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiConfigurationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/ai-configuration")
public class AiConfigurationController {
 private final AiConfigurationService service;
 @GetMapping("/channels") public Result<java.util.List<AiChannelResponse>> channels(@AuthenticationPrincipal AuthenticatedUser user) { return Result.ok(service.channels(user.userId())); }
 @PutMapping("/channels/{id}") public Result<AiChannelResponse> saveChannel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id,@Valid @RequestBody AiChannelRequest request) { return Result.ok(service.saveChannel(user.userId(),id,request)); }
 @GetMapping public Result<AiConfigurationResponse> get(@AuthenticationPrincipal AuthenticatedUser user) { return Result.ok(service.get(user.userId())); }
 @PutMapping public Result<AiConfigurationResponse> save(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody AiConfigurationRequest request) { return Result.ok(service.save(user.userId(),request)); }
 @PostMapping("/test") public Result<Boolean> test(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody(required=false) AiProviderProbeRequest request) { service.test(user.userId(),request); return Result.ok(true); }
 @PostMapping("/models") public Result<java.util.List<AiConfiguredModel>> models(@AuthenticationPrincipal AuthenticatedUser user,@Valid @RequestBody AiProviderProbeRequest request) { return Result.ok(service.discover(user.userId(),request)); }
}
