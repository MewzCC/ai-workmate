package com.aiworkmate.agent.tool.port;

import java.util.List;

public interface HrOrganizationToolPort {
    Result query(long actorUserId, Query query);

    record Query(String keyword, int limit) {}

    record Result(List<Department> departments, List<Position> positions, List<Employee> employees) {
        public Result {
            departments = List.copyOf(departments);
            positions = List.copyOf(positions);
            employees = List.copyOf(employees);
        }
    }

    record Department(long id, String code, String name, Long parentId, int status) {}
    record Position(long id, String code, String name, int status) {}
    record Employee(long id, String name, String role, int status,
                    Long departmentId, Long positionId, String approverName) {}
}
