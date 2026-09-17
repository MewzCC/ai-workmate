package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.agent.tool.port.VisitorToolPort;
import com.aiworkmate.service.AdminAssetsService;
import com.aiworkmate.service.model.VisitorAgentApplicationCommand;
import com.aiworkmate.service.model.VisitorAgentVisitCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public final class VisitorAgentDomainToolAdapter implements VisitorToolPort {
    private final AdminAssetsService adminAssetsService;

    @Override
    public Page query(ToolActorContext context, Query query) {
        if (query.bookingId() != null) {
            return new Page(List.of(toItem(adminAssetsService.getVisitorBooking(
                    context.userId(), query.bookingId()))), 1, 1, 1);
        }
        var result = query.queue() == Queue.PENDING
                ? adminAssetsService.listPendingVisitorBookings(context.userId(), query.page(), query.size())
                : adminAssetsService.listMyVisitorBookings(context.userId(), query.status(), query.page(), query.size());
        return new Page(result.records().stream().map(this::toItem).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public ApplicationResult apply(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey) {
        var result = adminAssetsService.submitVisitorBookingAgent(
                context.userId(), toDomain(command), operationKey.value());
        return new ApplicationResult(
                result.bookingId(), result.status(), result.version(), result.submittedAt());
    }

    @Override
    public ToolWriteVerification<ApplicationResult> findApplication(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey) {
        return adminAssetsService.findAgentVisitorBooking(
                        context.userId(), toDomain(command), operationKey.value())
                .map(result -> ToolWriteVerification.observed(new ApplicationResult(
                        result.bookingId(), result.status(), result.version(), result.submittedAt())))
                .orElseGet(ToolWriteVerification::unobserved);
    }

    private VisitorAgentApplicationCommand toDomain(ApplicationCommand command) {
        return new VisitorAgentApplicationCommand(
                command.visitorName(), command.visitorCompany(), command.visitorPhone(), command.purpose(),
                command.hostUserId(), command.expectedVisitAt(), command.expectedLeaveAt(),
                command.plateNumber(), command.partySize());
    }

    @Override
    public VisitResult checkIn(
            ToolActorContext context, VisitCommand command, ToolOperationKey operationKey) {
        var result = adminAssetsService.checkInVisitorAgent(
                context.userId(), toDomain(command), operationKey.value());
        return new VisitResult(
                result.bookingId(), result.status(), result.version(), result.occurredAt());
    }

    @Override
    public ToolWriteVerification<VisitResult> findCheckIn(
            ToolActorContext context, VisitCommand command, ToolOperationKey operationKey) {
        return adminAssetsService.findAgentVisitorCheckIn(
                        context.userId(), toDomain(command), operationKey.value())
                .map(result -> ToolWriteVerification.observed(new VisitResult(
                        result.bookingId(), result.status(), result.version(), result.occurredAt())))
                .orElseGet(ToolWriteVerification::unobserved);
    }

    private VisitorAgentVisitCommand toDomain(VisitCommand command) {
        return new VisitorAgentVisitCommand(command.bookingId(), command.version(), command.remark());
    }

    private Item toItem(com.aiworkmate.dto.VisitorBookingResponse item) {
        return new Item(item.id(), item.applicantName(), item.approverName(), item.hostName(),
                item.visitorName(), item.visitorCompany(), item.purpose(), item.expectedVisitAt(),
                item.expectedLeaveAt(), item.partySize(), item.status(), item.version(), item.taskStatus(),
                item.submittedAt(), item.completedAt(), item.registeredByName(), item.checkedInAt(), item.visitedAt(),
                item.leftAt(), item.noShowAt(), item.canWithdraw(), item.canDecide(), item.canCheckIn(),
                item.canMarkVisited(), item.canLeave(), item.canMarkNoShow());
    }
}
