package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.profile")
public class ProfileProperties {

    private long avatarMaxBytes = 2 * 1024 * 1024;

    /** 头像在对象存储中的 key 前缀 */
    private String avatarStoragePrefix = "user-avatars/";

    private long wallpaperMaxBytes = 5 * 1024 * 1024;

    /** 用户壁纸在对象存储中的 key 前缀 */
    private String wallpaperStoragePrefix = "user-wallpapers/";
}
