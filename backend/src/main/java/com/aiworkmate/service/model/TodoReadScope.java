package com.aiworkmate.service.model;

import java.util.Set;

/** Server-owned domain visibility, reused by list/count/dashboard/export; never accepted from request parameters. */
public record TodoReadScope(boolean visitorReadable, boolean sealReadable) {
    public static final Set<String> BUSINESS_TYPES=Set.of("ALL","LEAVE_APPLICATION","GENERIC_APPROVAL","VISITOR_BOOKING","SEAL_USAGE");
    public static TodoReadScope from(ResolvedUserAccess actor) {
        java.util.Objects.requireNonNull(actor);
        return new TodoReadScope(actor.permissions().contains("visitor:read:self"),actor.permissions().contains("seal:read:self"));
    }
    public boolean allows(String type) {
        return BUSINESS_TYPES.contains(type) && (!"VISITOR_BOOKING".equals(type) || visitorReadable)
                && (!"SEAL_USAGE".equals(type) || sealReadable);
    }
}
