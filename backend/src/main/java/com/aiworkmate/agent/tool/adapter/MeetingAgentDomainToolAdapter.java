package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.MeetingBookingRequest;
import com.aiworkmate.dto.MeetingBookingCancelRequest;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.MeetingBookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Local meeting boundary; future remote adapters implement the same typed port. */
@Component
@RequiredArgsConstructor
public final class MeetingAgentDomainToolAdapter implements MeetingToolPort {
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
    public MeetingToolPort.WriteResult book(
            ToolActorContext context, MeetingToolPort.BookCommand command, String operationKey) {
        var item = meetingBookingService.createAgent(context.userId(), new MeetingBookingRequest(
                command.roomId(), command.title(), command.agenda(), command.startAt(), command.endAt(),
                command.attendeeCount()), operationKey);
        return new MeetingToolPort.WriteResult(item.id(), item.roomId(), item.status(), item.version(),
                item.startAt(), item.endAt());
    }

    @Override
    public MeetingToolPort.CancelResult cancel(
            ToolActorContext context, MeetingToolPort.CancelCommand command, String operationKey) {
        var item = meetingBookingService.cancelAgent(context.userId(), command.bookingId(),
                new MeetingBookingCancelRequest(command.version(), command.reason()), operationKey);
        return new MeetingToolPort.CancelResult(item.id(), item.roomId(), item.status(), item.version(),
                item.cancelledAt());
    }

    @Override
    public java.util.Optional<MeetingToolPort.WriteResult> findCreation(
            ToolActorContext context, MeetingToolPort.BookCommand command, String operationKey) {
        return meetingBookingService.findAgentCreation(context.userId(), new MeetingBookingRequest(
                command.roomId(), command.title(), command.agenda(), command.startAt(), command.endAt(),
                command.attendeeCount()), operationKey).map(item -> new MeetingToolPort.WriteResult(
                item.id(), item.roomId(), item.status(), item.version(), item.startAt(), item.endAt()));
    }

}
