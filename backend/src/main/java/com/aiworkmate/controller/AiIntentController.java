package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AiIntentRequest;
import com.aiworkmate.dto.AiIntentResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.AiIntentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/intents")
@RequiredArgsConstructor
public class AiIntentController {
    private final AiIntentService intentService;
    @PostMapping
    public Result<AiIntentResponse> classify(@Valid @RequestBody AiIntentRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(intentService.classify(request, user));
    }
}
