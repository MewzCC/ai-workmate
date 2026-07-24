package com.aiworkmate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.profile")
public class ProfileProperties {

    private String avatarDirectory = "./data/user-avatars";
    private long avatarMaxBytes = 2 * 1024 * 1024;
}
