package com.aiworkmate.controller;

import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.UserProfileService;
import com.aiworkmate.service.model.AvatarContent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserAvatarController {

    private final UserProfileService userProfileService;

    @GetMapping("/{userId}/avatar/content")
    public ResponseEntity<org.springframework.core.io.Resource> avatar(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long userId) {
        AvatarContent content = userProfileService.loadAvatarByUser(user.userId(), userId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .body(content.resource());
    }
}
