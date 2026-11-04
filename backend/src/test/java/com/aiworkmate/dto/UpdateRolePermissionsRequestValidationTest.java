package com.aiworkmate.dto;

import com.aiworkmate.agent.registry.ToolCode;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateRolePermissionsRequestValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    @AfterAll static void closeValidator() { FACTORY.close(); }

    @Test void acceptsEveryCodeOwnedToolPermissionIncludingDottedAndCamelCaseNames() {
        var permissions = Arrays.stream(ToolCode.values()).map(tool -> "agent:tool:" + tool.code()).collect(Collectors.toSet());
        assertThat(FACTORY.getValidator().validate(new UpdateRolePermissionsRequest(permissions))).isEmpty();
    }

    @Test void acceptsExistingBusinessAndRouteNamespaces() {
        assertThat(FACTORY.getValidator().validate(new UpdateRolePermissionsRequest(Set.of(
                "notification:read:self", "user-permission:read:self", "route:messages", "access:manage")))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"agent:tool:*", "notification:", "a::b", " route:messages", "route:messages' OR 1=1", "http://example.invalid",
            "agent:tool:todo..query", "agent:tool:.query", "agent:tool:todo.query.", "Agent:tool:todo.query"})
    void rejectsMalformedOrWildcardAuthority(String code) {
        assertThat(FACTORY.getValidator().validate(new UpdateRolePermissionsRequest(Set.of(code)))).isNotEmpty();
    }

    @Test void rejectsNullEntriesAndCodesExceedingTheDatabaseLimit() {
        Set<String> nullEntry = new HashSet<>(); nullEntry.add(null);
        assertThat(FACTORY.getValidator().validate(new UpdateRolePermissionsRequest(nullEntry))).isNotEmpty();
        assertThat(FACTORY.getValidator().validate(new UpdateRolePermissionsRequest(Set.of("agent:tool:" + "a".repeat(81))))).isNotEmpty();
    }
}
