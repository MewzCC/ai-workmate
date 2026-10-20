package com.aiworkmate.agent.tool.port;

/** Room metadata is separate from reservation lifecycle and transport implementation. */
public interface MeetingRoomToolPort {
    ToolPage<Room> query(ToolActorContext actor, Query query);
    Receipt create(ToolActorContext actor, Fields fields);
    Receipt update(ToolActorContext actor, Update command);
    record Query(String keyword, String status, int page, int size) { }
    record Fields(String code, String name, String location, Integer capacity, String facilities, String status, String remark) { }
    record Update(long roomId, int version, Fields fields) { }
    record Room(long roomId, String code, String name, String location, int capacity,
                String facilities, String status, String remark, int version) { }
    record Receipt(long roomId, String code, String name, String status, int version) implements ToolWriteReceipt { }
}
