package com.aiworkmate.security;

public record AuthenticatedUser(Long userId, String username, String role) {
}
