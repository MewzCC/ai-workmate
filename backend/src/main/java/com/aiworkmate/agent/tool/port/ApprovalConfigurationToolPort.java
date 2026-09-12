package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Tenant-scoped read boundary for approval forms, processes and rules. */
public interface ApprovalConfigurationToolPort {
    Page query(Long actorUserId, Query query);

    enum Resource { FORM, PROCESS, RULE }

    record Query(Resource resource, String keyword, String status, int page, int size) { }

    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }

    record Item(long id, Resource resource, String key, String name, String description,
                String status, int version, String formName, String ruleType,
                Integer priority, LocalDateTime updatedAt) { }
}
