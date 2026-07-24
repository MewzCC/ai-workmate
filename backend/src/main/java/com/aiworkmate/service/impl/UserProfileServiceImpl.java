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
import com.aiworkmate.service.model.AvatarContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private final UserMapper userMapper;
    private final ProfileProperties properties;
    private final UserAccessService userAccessService;
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
        Path root = avatarRoot();
        String storageName = UUID.randomUUID().toString().replace("-", "") + extension;
        Path target = resolveInside(root, storageName);
        String previousAvatar = user.getAvatar();
        try {
            Files.createDirectories(root);
            file.transferTo(target);
            user.setAvatar(storageName);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            scheduleFileCleanup(root, storageName, previousAvatar);
            log.info("User avatar updated, userId={}", userId);
            return toResponse(user);
        } catch (IOException ex) {
            deleteQuietly(root, storageName);
            log.error("User avatar storage failed, userId={}", userId, ex);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "头像保存失败，请稍后重试");
        } catch (RuntimeException ex) {
            deleteQuietly(root, storageName);
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
        deleteQuietly(avatarRoot(), previousAvatar);
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
        Path path = resolveInside(avatarRoot(), user.getAvatar());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "头像文件不存在");
        }
        String mimeType = detectStoredMimeType(path);
        if (!AVATAR_EXTENSIONS.containsKey(mimeType)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "头像文件类型无效");
        }
        return new AvatarContent(new FileSystemResource(path), mimeType);
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

    private String detectStoredMimeType(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            return tika.detect(input, path.getFileName().toString());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "无法读取头像文件");
        }
    }

    private User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return user;
    }

    private Path avatarRoot() {
        return Path.of(properties.getAvatarDirectory()).toAbsolutePath().normalize();
    }

    private Path resolveInside(Path root, String storageName) {
        Path resolved = root.resolve(storageName).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "头像存储路径无效");
        }
        return resolved;
    }

    private void deleteQuietly(Path root, String storageName) {
        if (storageName == null || storageName.isBlank()) return;
        try {
            Files.deleteIfExists(resolveInside(root, storageName));
        } catch (IOException ex) {
            log.warn("Unable to delete old avatar file, storageName={}", storageName);
        }
    }

    private void scheduleFileCleanup(Path root, String newAvatar, String previousAvatar) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    deleteQuietly(root, previousAvatar);
                } else {
                    deleteQuietly(root, newAvatar);
                }
            }
        });
    }

    private AuthUserResponse toResponse(User user) {
        String name = user.getDisplayName() == null || user.getDisplayName().isBlank()
                ? user.getUsername() : user.getDisplayName();
        String avatarUrl = user.getAvatar() == null || user.getAvatar().isBlank()
                ? null : "/api/profile/avatar/content?v=" + user.getUpdatedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
        return new AuthUserResponse(
                user.getId(),
                name,
                user.getEmail(),
                user.getRole(),
                avatarUrl,
                userAccessService.permissionsForRole(user.getRole())
        );
    }
}
