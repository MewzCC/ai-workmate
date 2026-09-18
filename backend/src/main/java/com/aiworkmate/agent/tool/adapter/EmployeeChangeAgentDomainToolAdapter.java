package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.agent.tool.port.ToolOperationKey;
import com.aiworkmate.agent.tool.port.ToolWriteVerification;
import com.aiworkmate.service.EmployeeChangeService;
import com.aiworkmate.service.model.EmployeeChangeAgentApplicationCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class EmployeeChangeAgentDomainToolAdapter implements EmployeeChangeToolPort {
    private final EmployeeChangeService employeeChangeService;

    @Override
    public Page query(ToolActorContext context, Query query) {
        var result = employeeChangeService.list(context.userId(), query.status(), query.changeType(), query.keyword(),
                query.page(), query.size());
        return new Page(result.records().stream().map(item ->
                new Item(item.id(), item.employeeName(), item.applicantName(), item.reviewApproverName(),
                        item.changeType(), item.effectiveDate(), item.currentDepartmentName(),
                        item.currentPositionName(), item.targetDepartmentName(), item.targetPositionName(),
                        item.targetSupervisorName(), item.reason(), item.status(), item.version(), item.canApprove(),
                        item.canWithdraw(), item.submittedAt(), item.decidedAt(), item.appliedAt())).toList(),
                result.total(), result.page(), result.size());
    }

    @Override
    public ApplicationResult apply(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey) {
        var result = employeeChangeService.createAgent(
                context.userId(), toDomain(command), operationKey.value());
        return new ApplicationResult(
                result.changeId(), result.status(), result.version(), result.submittedAt());
    }

    @Override
    public ToolWriteVerification<ApplicationResult> findApplication(
            ToolActorContext context, ApplicationCommand command, ToolOperationKey operationKey) {
        return employeeChangeService.findAgentApplication(
                        context.userId(), toDomain(command), operationKey.value())
                .map(result -> ToolWriteVerification.observed(new ApplicationResult(
                        result.changeId(), result.status(), result.version(), result.submittedAt())))
                .orElseGet(ToolWriteVerification::unobserved);
    }

    private EmployeeChangeAgentApplicationCommand toDomain(ApplicationCommand command) {
        return new EmployeeChangeAgentApplicationCommand(
                command.employeeUserId(), command.changeType(), command.effectiveDate(),
                command.targetDepartmentId(), command.targetPositionId(), command.targetSupervisorUserId(),
                command.reviewApproverUserId(), command.reason());
    }
}
