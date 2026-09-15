package com.aiworkmate.agent.tool.port;

import java.time.LocalDateTime;
import java.util.List;

/** Self-owned notification query port. */
public interface NotificationToolPort {
    Page mine(ToolActorContext context, int page, int size);

    ReadResult markRead(ToolActorContext context, long notificationId);

    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
    record Item(long id, String type, String title, String content, String businessType,
                boolean read, LocalDateTime createdAt) { }
    record ReadResult(long notificationId, boolean read) { }
}
