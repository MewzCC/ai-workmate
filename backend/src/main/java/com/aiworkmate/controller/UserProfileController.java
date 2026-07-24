package com.aiworkmate.controller;

import com.aiworkmate.common.Result;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.UpdateProfileRequest;
import com.aiworkmate.security.AuthenticatedUser;
import com.aiworkmate.service.UserProfileService;
import com.aiworkmate.service.model.AvatarContent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @PutMapping
    public Result<AuthUserResponse> update(@Valid @RequestBody UpdateProfileRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(userProfileService.update(user.userId(), request));
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<AuthUserResponse> uploadAvatar(@RequestPart("file") MultipartFile file,
                                                 @AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(userProfileService.uploadAvatar(user.userId(), file));
    }

    @DeleteMapping("/avatar")
    public Result<AuthUserResponse> deleteAvatar(@AuthenticationPrincipal AuthenticatedUser user) {
        return Result.ok(userProfileService.deleteAvatar(user.userId()));
    }

    @GetMapping("/avatar/content")
    public ResponseEntity<org.springframework.core.io.Resource> avatar(
            @AuthenticationPrincipal AuthenticatedUser user) {
        AvatarContent content = userProfileService.loadAvatar(user.userId());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(content.mimeType()))
                .body(content.resource());
    }
}
