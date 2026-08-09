package com.aiworkmate.service;

import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.ChangePasswordRequest;
import com.aiworkmate.dto.UpdateProfileRequest;
import com.aiworkmate.dto.WallpaperResponse;
import com.aiworkmate.service.model.AvatarContent;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    AuthUserResponse update(Long userId, UpdateProfileRequest request);

    /** 已登录用户凭旧密码修改为新密码；旧密码错误或新旧相同时抛 BusinessException。 */
    void changePassword(Long userId, ChangePasswordRequest request);

    AuthUserResponse uploadAvatar(Long userId, MultipartFile file);

    AuthUserResponse deleteAvatar(Long userId);

    AvatarContent loadAvatar(Long userId);

    AvatarContent loadAvatarByUser(Long actorUserId, Long targetUserId);

    WallpaperResponse getWallpaper(Long userId);

    WallpaperResponse uploadWallpaper(Long userId, MultipartFile file);

    WallpaperResponse deleteWallpaper(Long userId);

    AvatarContent loadWallpaper(Long userId);
}
