package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.NavigationRouteResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.NavigationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/navigation")
@RequiredArgsConstructor
public class NavigationController {

    private final NavigationService navigationService;

    @GetMapping
    public Result<List<NavigationRouteResponse>> navigation(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(navigationService.navigation(user.userId()));
    }
}
