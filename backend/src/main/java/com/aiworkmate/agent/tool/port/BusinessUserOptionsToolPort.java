package com.aiworkmate.agent.tool.port;

import java.util.List;

public interface BusinessUserOptionsToolPort {
    Page reviewers(ToolActorContext actor, Query query);
    Page visitorHosts(ToolActorContext actor, Query query);
    record Query(String keyword, int page, int size) {}
    record Item(long id, String name) {}
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
}
