package com.aiworkmate.controller;

import com.aiworkmate.common.PageResponse;
import com.aiworkmate.common.Result;
import com.aiworkmate.dto.NotificationResponse;
import com.aiworkmate.dto.UnreadCountResponse;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知：列表 / 未读数 / 标记已读 / 全部已读。
 * 全部接口按 JWT 认证 userId 校验归属。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(notificationService.list(user.userId(), page, size));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(new UnreadCountResponse(notificationService.unreadCount(user.userId())));
    }

    @PatchMapping("/{id}/read")
    public Result<Void> markRead(@AuthenticationPrincipal AuthenticatedUser user,
                                 @PathVariable long id) {
        notificationService.markRead(user.userId(), id);
        return Result.ok(null);
    }

    @PatchMapping("/read-all")
    public Result<Long> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(notificationService.markAllRead(user.userId()));
    }
}
