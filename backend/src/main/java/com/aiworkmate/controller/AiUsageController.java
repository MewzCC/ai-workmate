package com.aiworkmate.controller;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AiUsageBucket;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequiredArgsConstructor
@RequestMapping("/api/admin/ai-configuration/usage")
public class AiUsageController {
 private final AiUsageService service;
 @GetMapping public Result<List<AiUsageBucket>> report(@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="7") int days) {
  return Result.ok(service.report(user.userId(),days));
 }
}
