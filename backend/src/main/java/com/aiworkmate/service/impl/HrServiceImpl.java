package com.aiworkmate.service.impl;

import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.OrganizationOverviewResponse;
import com.aiworkmate.dto.PositionResponse;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.service.HrService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrServiceImpl implements HrService {

    private final AccessControlMapper accessControlMapper;

    @Override
    public OrganizationOverviewResponse overview(Long tenantId) {
        List<DepartmentResponse> departments = accessControlMapper.selectDepartments(tenantId);
        List<PositionResponse> positions = accessControlMapper.selectPositions(tenantId);
        List<AccessUserRow> users = accessControlMapper.selectUsers(tenantId);

        Map<Long, String> nameMap = users.stream()
                .collect(Collectors.toMap(AccessUserRow::id, AccessUserRow::name, (a, b) -> a));

        List<OrganizationOverviewResponse.EmployeeSummary> employees = users.stream()
                .map(user -> new OrganizationOverviewResponse.EmployeeSummary(
                        user.id(),
                        user.name(),
                        user.email(),
                        user.role(),
                        user.status(),
                        user.departmentId(),
                        user.positionId(),
                        user.approverUserId(),
                        user.approverUserId() != null ? nameMap.get(user.approverUserId()) : null
                ))
                .toList();

        return new OrganizationOverviewResponse(departments, positions, employees);
    }
}
