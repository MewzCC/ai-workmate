package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.MeetingRoomToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolPage;
import com.aiworkmate.dto.MeetingRoomResponse;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.model.MeetingRoomAgentFields;
import lombok.RequiredArgsConstructor;

@LocalAgentDomainAdapter
@RequiredArgsConstructor
public final class MeetingRoomAgentDomainToolAdapter implements MeetingRoomToolPort {
    private final AdminAssetsService service;
    @Override public ToolPage<Room> query(ToolActorContext actor, Query query) {
        var page = service.listMeetingRooms(actor.userId(), query.keyword(), query.status(), query.page(), query.size());
        return new ToolPage<>(page.records().stream().map(value -> new Room(value.id(), value.code(), value.name(),
                value.location(), value.capacity(), value.facilities(), value.status(), value.remark(), value.version())).toList(),
                page.total(), page.page(), page.size());
    }
    @Override public Receipt create(ToolActorContext actor, Fields fields) {
        return receipt(service.createMeetingRoomAgent(actor.userId(), domain(fields)));
    }
    @Override public Receipt update(ToolActorContext actor, Update command) {
        return receipt(service.updateMeetingRoomAgent(actor.userId(), command.roomId(), command.version(), domain(command.fields())));
    }
    private MeetingRoomAgentFields domain(Fields value) {
        return new MeetingRoomAgentFields(value.code(), value.name(), value.location(), value.capacity(),
                value.facilities(), value.status(), value.remark());
    }
    private Receipt receipt(MeetingRoomResponse value) {
        return new Receipt(value.id(), value.code(), value.name(), value.status(), value.version());
    }
}
