package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.AssetToolPort;
import com.aiworkmate.agent.tool.port.MeetingToolPort;
import com.aiworkmate.agent.tool.port.SealToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.MeetingBookingService;
import com.aiworkmate.dto.MeetingBookingRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdministrativeAssetsAgentDomainToolAdapter
        implements AssetToolPort, MeetingToolPort, VisitorToolPort, SealToolPort {
    private final AdminAssetsService adminAssetsService;
    private final MeetingBookingService meetingBookingService;

    @Override
    public VisitorToolPort.Page query(ToolActorContext context, VisitorToolPort.Query query) {
        if (query.bookingId() != null) {
            return new VisitorToolPort.Page(List.of(visitor(adminAssetsService.getVisitorBooking(
                    context.userId(), query.bookingId()))), 1, 1, 1);
        }
        var result = query.queue() == VisitorToolPort.Queue.PENDING
                ? adminAssetsService.listPendingVisitorBookings(context.userId(), query.page(), query.size())
                : adminAssetsService.listMyVisitorBookings(context.userId(), query.status(), query.page(), query.size());
        return new VisitorToolPort.Page(result.records().stream().map(this::visitor).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public SealToolPort.Page query(ToolActorContext context, SealToolPort.Query query) {
        if (query.usageId() != null) {
            return new SealToolPort.Page(List.of(seal(adminAssetsService.getSealUsage(
                    context.userId(), query.usageId()))), 1, 1, 1);
        }
        var result = query.queue() == SealToolPort.Queue.PENDING
                ? adminAssetsService.listPendingSealUsages(context.userId(), query.page(), query.size())
                : adminAssetsService.listMySealUsages(context.userId(), query.status(), query.page(), query.size());
        return new SealToolPort.Page(result.records().stream().map(this::seal).toList(),
                result.total(), result.page(), result.size());
    }

    private VisitorToolPort.Item visitor(com.aiworkmate.dto.VisitorBookingResponse item) {
        return new VisitorToolPort.Item(item.id(), item.applicantName(), item.approverName(), item.hostName(),
                item.visitorName(), item.visitorCompany(), item.purpose(), item.expectedVisitAt(),
                item.expectedLeaveAt(), item.partySize(), item.status(), item.version(), item.taskStatus(),
                item.submittedAt(), item.completedAt(), item.registeredByName(), item.checkedInAt(), item.visitedAt(),
                item.leftAt(), item.noShowAt(), item.canWithdraw(), item.canDecide(), item.canCheckIn(),
                item.canMarkVisited(), item.canLeave(), item.canMarkNoShow());
    }

    private SealToolPort.Item seal(com.aiworkmate.dto.SealUsageResponse item) {
        return new SealToolPort.Item(item.id(), item.applicantName(), item.approverName(), item.sealType(),
                item.documentTitle(), item.usageReason(), item.copies(), item.status(), item.version(),
                item.taskStatus(), item.submittedAt(), item.completedAt(), item.actualCopies(), item.handlerName(),
                item.usedAt(), item.returnedAt(), item.canWithdraw(), item.canDecide(), item.canRegisterUse(),
                item.canReturn(), item.canArchiveDocument());
    }

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
