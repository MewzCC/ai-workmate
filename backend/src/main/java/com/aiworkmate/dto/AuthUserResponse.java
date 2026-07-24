package com.aiworkmate.dto;

public record AuthUserResponse(Long id, String name, String email, String role, String avatarUrl) {
}
