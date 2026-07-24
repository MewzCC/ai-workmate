package com.aiworkmate.common.validation;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    private final Pattern pattern = Pattern.compile(PasswordPolicy.PASSWORD_REGEX);

    @Test
    void shouldAcceptPasswordsAtMediumStrengthOrAbove() {
        assertThat(pattern.matcher("admin123").matches()).isTrue();
        assertThat(pattern.matcher("Zcc234@1").matches()).isTrue();
    }

    @Test
    void shouldRejectPasswordsBelowMediumStrength() {
        assertThat(pattern.matcher("abcdefgh").matches()).isFalse();
        assertThat(pattern.matcher("12345678").matches()).isFalse();
        assertThat(pattern.matcher("abc123").matches()).isFalse();
    }
}
