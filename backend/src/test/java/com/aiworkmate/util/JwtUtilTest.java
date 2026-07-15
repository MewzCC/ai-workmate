package com.aiworkmate.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private static final String SECRET = "test-jwt-secret-key-that-is-long-enough-for-hmac-sha";

    @Test
    void shouldGenerateTokenWithUserClaims() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000L);

        String token = jwtUtil.generateToken(1001L, "alice", "ADMIN");

        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.getUserIdFromToken(token)).isEqualTo(1001L);
        assertThat(jwtUtil.getUsernameFromToken(token)).isEqualTo("alice");
        assertThat(jwtUtil.getRoleFromToken(token)).isEqualTo("ADMIN");
    }

    @Test
    void shouldRejectTamperedToken() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 60_000L);

        String token = jwtUtil.generateToken(1001L, "alice", "USER") + "tampered";

        assertThat(jwtUtil.validateToken(token)).isFalse();
    }
}
