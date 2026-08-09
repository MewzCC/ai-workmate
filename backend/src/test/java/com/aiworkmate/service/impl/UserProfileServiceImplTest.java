package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.config.ProfileProperties;
import com.aiworkmate.dto.AuthUserResponse;
import com.aiworkmate.dto.UpdateProfileRequest;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserAccessService userAccessService;

    @Mock
    private ObjectStorageService objectStorageService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        ProfileProperties properties = new ProfileProperties();
        profileService = new UserProfileServiceImpl(userMapper, properties, userAccessService,
                objectStorageService, passwordEncoder);
        org.mockito.Mockito.lenient()
                .when(userAccessService.permissionsForRole(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.List.of());
    }

    @Test
    void shouldUpdateOnlyAuthenticatedUsersProfile() {
        User user = activeUser();
        when(userMapper.selectById(1001L)).thenReturn(user);

        AuthUserResponse response = profileService.update(1001L, new UpdateProfileRequest("Alice Chen"));

        assertThat(response.name()).isEqualTo("Alice Chen");
        verify(userMapper).updateById(user);
    }

    @Test
    void shouldRejectFileWhoseRealTypeIsNotAnImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", "not an image".getBytes());

        assertThatThrownBy(() -> profileService.uploadAvatar(1001L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("头像仅支持 JPG、PNG 或 WebP");

        verifyNoInteractions(userMapper);
        verifyNoInteractions(objectStorageService);
    }

    @Test
    void shouldDetectAndStoreRealPngAvatar() {
        User user = activeUser();
        when(userMapper.selectById(1001L)).thenReturn(user);
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", ONE_PIXEL_PNG);

        TransactionSynchronizationManager.initSynchronization();
        try {
            AuthUserResponse response = profileService.uploadAvatar(1001L, file);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_COMMITTED));

            assertThat(response.avatarUrl()).startsWith("/api/profile/avatar/content?v=");
            assertThat(user.getAvatar()).endsWith(".png");
            verify(objectStorageService).store(anyString(), any(), eq((long) ONE_PIXEL_PNG.length), eq("image/png"));
            verify(userMapper).updateById(user);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldStoreWallpaperInDedicatedMinioPrefix() {
        User user = activeUser();
        when(userMapper.selectById(1001L)).thenReturn(user);
        MockMultipartFile file = new MockMultipartFile(
                "file", "wallpaper.png", "image/png", ONE_PIXEL_PNG);

        TransactionSynchronizationManager.initSynchronization();
        try {
            var response = profileService.uploadWallpaper(1001L, file);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_COMMITTED));

            assertThat(response.wallpaperUrl()).startsWith("/api/profile/wallpaper/content?v=");
            assertThat(user.getWallpaper()).startsWith("user-wallpapers/").endsWith(".png");
            verify(objectStorageService).store(
                    org.mockito.ArgumentMatchers.startsWith("user-wallpapers/"),
                    any(),
                    eq((long) ONE_PIXEL_PNG.length),
                    eq("image/png"));
            verify(userMapper).updateById(user);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRejectOversizedWallpaperBeforeStorage() {
        ProfileProperties properties = new ProfileProperties();
        properties.setWallpaperMaxBytes(4);
        profileService = new UserProfileServiceImpl(userMapper, properties, userAccessService,
                objectStorageService, passwordEncoder);
        MockMultipartFile file = new MockMultipartFile(
                "file", "wallpaper.png", "image/png", ONE_PIXEL_PNG);

        assertThatThrownBy(() -> profileService.uploadWallpaper(1001L, file))
                .isInstanceOf(BusinessException.class)
                .hasMessage("壁纸大小不能超过 5MB");

        verifyNoInteractions(userMapper);
        verifyNoInteractions(objectStorageService);
    }

    private User activeUser() {
        User user = new User();
        user.setId(1001L);
        user.setUsername("alice@example.com");
        user.setDisplayName("Alice");
        user.setEmail("alice@example.com");
        user.setRole("EMPLOYEE");
        user.setStatus(1);
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
