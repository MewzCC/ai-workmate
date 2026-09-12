package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.MeetingBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdministrativeAssetsAgentDomainToolAdapter implements AssetToolPort, MeetingToolPort {
    private final AdminAssetsService adminAssetsService;
    private final MeetingBookingService meetingBookingService;

    @Override
    public MeetingToolPort.Result query(ToolActorContext context, MeetingToolPort.Query query) {
        var rooms = adminAssetsService.listMeetingRooms(
                context.userId(), query.keyword(), query.roomStatus(), 1, 50);
        var bookings = meetingBookingService.listMine(
                context.userId(), query.from(), query.to(), query.bookingStatus(), query.page(), query.size());
        return new MeetingToolPort.Result(rooms.records().stream().map(item -> new MeetingToolPort.Room(
                item.id(), item.code(), item.name(), item.location(), item.capacity(), item.facilities(), item.status(),
                item.remark(), item.canEdit(), item.canDelete())).toList(), bookings.records().stream().map(item ->
                new MeetingToolPort.Booking(item.id(), item.roomId(), item.roomCode(), item.roomName(),
                        item.roomLocation(), item.organizerName(), item.title(), item.agenda(), item.startAt(),
                        item.endAt(), item.attendeeCount(), item.status(), item.version(), item.cancelledByName(),
                        item.cancelledAt(), item.cancelReason(), item.createdAt(), item.updatedAt(), item.canCancel()))
                .toList(), bookings.total(), bookings.page(), bookings.size());
    }

    @Override
    public AssetToolPort.Page query(ToolActorContext context, AssetToolPort.Query query) {
        var result = adminAssetsService.listAssets(
                context.userId(), query.keyword(), query.category(), query.status(), query.page(), query.size());
        return new AssetToolPort.Page(result.records().stream().map(item -> new AssetToolPort.Item(
                item.id(), item.assetCode(), item.name(), item.category(), item.specification(), item.status(),
                item.departmentName(), item.ownerName(), item.purchaseDate(), item.originalValue(), item.remark(),
                item.version(), item.canEdit(), item.canDelete(), item.createdAt(), item.updatedAt())).toList(),
                result.total(), result.page(), result.size());
    }
}
