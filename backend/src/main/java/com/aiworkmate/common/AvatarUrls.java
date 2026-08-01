package com.aiworkmate.common;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class AvatarUrls {

    private AvatarUrls() {
    }

    public static String build(Long userId, String avatar, LocalDateTime updatedAt) {
        if (userId == null || avatar == null || avatar.isBlank() || updatedAt == null) {
            return null;
        }
        return "/api/users/" + userId + "/avatar/content?v="
                + updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
