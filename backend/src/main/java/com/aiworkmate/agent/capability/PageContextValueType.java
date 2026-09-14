package com.aiworkmate.agent.capability;

/**
 * Page context intentionally supports scalar values only. Identity, tenant,
 * permission and data-scope facts are rebuilt from the authenticated request.
 */
public enum PageContextValueType {
    STRING,
    NUMBER,
    BOOLEAN
}
