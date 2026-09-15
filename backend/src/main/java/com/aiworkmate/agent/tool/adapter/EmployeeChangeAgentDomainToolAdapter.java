package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.EmployeeChangeToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.EmployeeChangeService;
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
}
