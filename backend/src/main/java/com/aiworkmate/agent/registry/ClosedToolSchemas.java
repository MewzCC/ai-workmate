package com.aiworkmate.agent.registry;

import java.util.regex.Pattern;

/** Deterministic closed-schema fragments shared by bounded Agent tools. */
public final class ClosedToolSchemas {
    private static final Pattern PROPERTY_NAME = Pattern.compile("^[a-z][A-Za-z0-9]*$");

    private ClosedToolSchemas() { }

    public static String versionedResourceInput(String idProperty) {
        requirePropertyName(idProperty);
        return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\""
                + idProperty + "\",\"version\"],\"properties\":{\"" + idProperty
                + "\":{\"type\":\"integer\",\"minimum\":1},\"version\":{\"type\":\"integer\","
                + "\"minimum\":0,\"maximum\":2147483646}}}";
    }

    public static String positiveIdInput(String idProperty) {
        requirePropertyName(idProperty);
        return "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\""
                + idProperty + "\"],\"properties\":{\"" + idProperty
                + "\":{\"type\":\"integer\",\"minimum\":1}}}";
    }

    private static void requirePropertyName(String propertyName) {
        if (propertyName == null || !PROPERTY_NAME.matcher(propertyName).matches()) {
            throw new IllegalArgumentException("Safe schema property name is required");
        }
    }
}
