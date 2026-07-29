package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.config.ProfileProperties;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.UpdateProfileRequest;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.UserProfileService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.ObjectStorageService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.aiworkmate.service.model.AvatarContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private static final Map<String, String> AVATAR_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );
    private static final Map<String, String> AVATAR_MIME_BY_EXTENSION = Map.of(
            ".jpg", "image/jpeg",
            ".png", "image/png",
            ".webp", "image/webp"
    );

    private final UserMapper userMapper;
    private final ProfileProperties properties;
    private final UserAccessService userAccessService;
    private final ObjectStorageService objectStorageService;
    private final Tika tika = new Tika();

    @Override
    @Transactional
    public AuthUserResponse update(Long userId, UpdateProfileRequest request) {
        User user = requireActiveUser(userId);
        user.setDisplayName(request.name().strip());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        log.info("User profile updated, userId={}", userId);
        return toResponse(user);
    }

    @Override
    @Transactional
    public AuthUserResponse uploadAvatar(Long userId, MultipartFile file) {
        validateSize(file);
        String mimeType = detectMimeType(file);
        String extension = AVATAR_EXTENSIONS.get(mimeType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "头像仅支持 JPG、PNG 或 WebP");
        }

        User user = requireActiveUser(userId);
        String storageName = buildAvatarKey(extension);
        String previousAvatar = user.getAvatar();
        try {
            objectStorageService.store(storageName, file.getInputStream(), file.getSize(), mimeType);
        } catch (IOException ex) {
            objectStorageService.delete(storageName);
            log.error("User avatar stream read failed, userId={}", userId, ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像保存失败，请稍后重试");
        } catch (RuntimeException ex) {
            objectStorageService.delete(storageName);
            log.error("User avatar storage failed, userId={}", userId, ex);
            throw ex instanceof BusinessException ? ex
                    : new BusinessException(ErrorCode.SYSTEM_ERROR, "头像保存失败，请稍后重试");
        }
        try {
            user.setAvatar(storageName);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            scheduleAvatarCleanup(storageName, previousAvatar);
            log.info("User avatar updated, userId={}", userId);
            return toResponse(user);
        } catch (RuntimeException ex) {
            objectStorageService.delete(storageName);
            throw ex;
        }
    }

    @Override
    @Transactional
    public AuthUserResponse deleteAvatar(Long userId) {
        User user = requireActiveUser(userId);
        String previousAvatar = user.getAvatar();
        user.setAvatar(null);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
        if (previousAvatar != null && !previousAvatar.isBlank()) {
            objectStorageService.delete(previousAvatar);
        }
        log.info("User avatar deleted, userId={}", userId);
        return toResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public AvatarContent loadAvatar(Long userId) {
        User user = requireActiveUser(userId);
        if (user.getAvatar() == null || user.getAvatar().isBlank()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "尚未设置头像");
        }
        String storageName = user.getAvatar();
        String mimeType = resolveMimeType(storageName);
        Resource resource = objectStorageService.load(storageName);
        return new AvatarContent(resource, mimeType);
    }

    private void validateSize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "请选择头像文件");
        }
        if (file.getSize() > properties.getAvatarMaxBytes()) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "头像大小不能超过 2MB");
        }
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return tika.detect(input, file.getOriginalFilename());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "无法识别头像文件");
        }
    }

    private String resolveMimeType(String storageName) {
        String lower = storageName.toLowerCase(Locale.ROOT);
        String mime = AVATAR_MIME_BY_EXTENSION.entrySet().stream()
                .filter(entry -> lower.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        if (mime == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "头像文件类型无效");
        }
        return mime;
    }

    private User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return user;
    }

    private String buildAvatarKey(String extension) {
        String prefix = properties.getAvatarStoragePrefix() == null || properties.getAvatarStoragePrefix().isBlank()
                ? "" : properties.getAvatarStoragePrefix();
        return prefix + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private void scheduleAvatarCleanup(String newAvatar, String previousAvatar) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    if (previousAvatar != null && !previousAvatar.isBlank()) {
                        objectStorageService.delete(previousAvatar);
                    }
                } else {
                    objectStorageService.delete(newAvatar);
                }
            }
        });
    }

    private AuthUserResponse toResponse(User user) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(user.getId());
        String name = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
        String avatarUrl = user.getAvatar() == null || user.getAvatar().isBlank()
                ? null : "/api/profile/avatar/content?v=" + user.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return new AuthUserResponse(
                user.getId(),
                name,
                user.getEmail(),
                access == null ? user.getTenantId() : access.tenantId(),
                access == null ? user.getRole() : access.role(),
                access == null ? List.of(user.getRole()) : access.roles(),
                avatarUrl,
                access == null ? userAccessService.permissionsForRole(user.getRole()) : access.permissions(),
                access == null ? List.of("SELF") : access.dataScopes(),
                access == null ? user.getPermissionVersion() : access.permissionVersion()
        );
    }
}
