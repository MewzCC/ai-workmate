package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.HrOrganizationToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.service.HrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public final class HrOrganizationAgentDomainToolAdapter implements HrOrganizationToolPort {
    private final HrService hrService;

    @Override
    public Result query(ToolActorContext context, Query query) {
        var overview = hrService.overviewForActor(context.userId());
        Predicate<String> matches = value -> query.keyword() == null ||
                (value != null && value.toLowerCase(Locale.ROOT).contains(query.keyword().toLowerCase(Locale.ROOT)));
        var departments = overview.departments().stream()
                .filter(item -> matches.test(item.name()) || matches.test(item.code()))
                .limit(query.limit()).map(item -> new Department(
                        item.id(), item.code(), item.name(), item.parentId(), item.status())).toList();
        var positions = overview.positions().stream()
                .filter(item -> matches.test(item.name()) || matches.test(item.code()))
                .limit(query.limit()).map(item -> new Position(
                        item.id(), item.code(), item.name(), item.status())).toList();
        var employees = overview.employees().stream()
                .filter(item -> matches.test(item.name()) || matches.test(item.role()))
                .limit(query.limit()).map(item -> new Employee(
                        item.id(), item.name(), item.role(), item.status(), item.departmentId(), item.positionId(),
                        item.approverName())).toList();
        return new Result(departments, positions, employees);
    }
}
