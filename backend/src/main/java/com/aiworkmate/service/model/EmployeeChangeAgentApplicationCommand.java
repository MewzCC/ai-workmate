package com.aiworkmate.service.model;

import java.time.LocalDate;

public record EmployeeChangeAgentApplicationCommand(
        long employeeUserId, String changeType, LocalDate effectiveDate,
        Long targetDepartmentId, Long targetPositionId, Long targetSupervisorUserId,
        long reviewApproverUserId, String reason) {
}
