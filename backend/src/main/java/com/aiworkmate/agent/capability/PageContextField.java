package com.aiworkmate.agent.capability;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record PageContextField(String name, PageContextValueType valueType, int maxLength) {
    private static final Pattern FIELD_NAME_PATTERN = Pattern.compile("^[a-z][A-Za-z0-9]{0,39}$");
    private static final Set<String> FORBIDDEN_NAME_PARTS = Set.of(
            "userid", "tenantid", "role", "permission", "datascope",
            "url", "uri", "sql", "filepath", "path", "script", "classname", "beanname"
    );

    public PageContextField {
        if (name == null || !FIELD_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid page context field name");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_NAME_PARTS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException("Forbidden page context field: " + name);
        }
        if (valueType == null) {
            throw new IllegalArgumentException("Page context value type is required");
        }
        if (maxLength < 1 || maxLength > 2000) {
            throw new IllegalArgumentException("Page context field maxLength exceeds platform limit");
        }
    }
}
