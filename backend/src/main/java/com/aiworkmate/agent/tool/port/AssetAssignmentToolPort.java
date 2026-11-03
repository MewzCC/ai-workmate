package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Minimal asset-domain lookup contract, independent of HTTP and persistence. */
public interface AssetAssignmentToolPort {
    Page departments(ToolActorContext actor, Query query);
    Page custodians(ToolActorContext actor, CustodianQuery query);
    record Query(String keyword, int page, int size) { }
    record CustodianQuery(long departmentId, String keyword, int page, int size) { }
    record Item(long id, String name, Long departmentId) { }
    record Page(List<Item> items, long total, int page, int size) {
        public Page { items = List.copyOf(items); }
    }
}
