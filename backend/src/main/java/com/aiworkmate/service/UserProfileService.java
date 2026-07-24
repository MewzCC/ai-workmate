package com.aiworkmate.service;

import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.UpdateProfileRequest;
import com.aiworkmate.service.model.AvatarContent;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    AuthUserResponse update(Long userId, UpdateProfileRequest request);

    AuthUserResponse uploadAvatar(Long userId, MultipartFile file);

    AuthUserResponse deleteAvatar(Long userId);

    AvatarContent loadAvatar(Long userId);
}
