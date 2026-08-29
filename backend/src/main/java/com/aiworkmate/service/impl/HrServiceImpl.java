package com.aiworkmate.service.impl;

import com.aiworkmate.common.AvatarUrls;
import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.EmployeeDetailResponse;
import com.aiworkmate.dto.OrganizationOverviewResponse;
import com.aiworkmate.dto.PositionResponse;
import com.aiworkmate.entity.AttendanceRecord;
import com.aiworkmate.entity.AttendanceReissue;
import com.aiworkmate.entity.LeaveApplication;
import com.aiworkmate.entity.EmployeeChange;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AttendanceRecordMapper;
import com.aiworkmate.mapper.AttendanceReissueMapper;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import com.aiworkmate.mapper.EmployeeChangeMapper;
import com.aiworkmate.service.HrService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrServiceImpl implements HrService {

    private static final int RECENT_LIMIT = 6;
    private static final int ACTIVITY_LIMIT = 8;

    private final AccessControlMapper accessControlMapper;
    private final AttendanceRecordMapper attendanceRecordMapper;
    private final AttendanceReissueMapper attendanceReissueMapper;
    private final LeaveApplicationMapper leaveApplicationMapper;
    private final EmployeeChangeMapper employeeChangeMapper;

    @Override
    public OrganizationOverviewResponse overview(Long tenantId) {
        List<DepartmentResponse> departments = accessControlMapper.selectDepartments(tenantId);
        List<PositionResponse> positions = accessControlMapper.selectPositions(tenantId);
        List<AccessUserRow> users = accessControlMapper.selectUsers(tenantId);

        Map<Long, AccessUserRow> userMap = users.stream()
                .collect(Collectors.toMap(AccessUserRow::id, user -> user, (a, b) -> a));

        List<OrganizationOverviewResponse.EmployeeSummary> employees = users.stream()
                .map(user -> {
                    AccessUserRow approver = user.approverUserId() != null
                            ? userMap.get(user.approverUserId()) : null;
                    return new OrganizationOverviewResponse.EmployeeSummary(
                            user.id(),
                            user.name(),
                            user.email(),
                            user.role(),
                            user.status(),
                            user.departmentId(),
                            user.positionId(),
                            user.approverUserId(),
                            approver != null ? approver.name() : null,
                            AvatarUrls.build(user.id(), user.avatar(), user.updatedAt()),
                            approver != null
                                    ? AvatarUrls.build(approver.id(), approver.avatar(), approver.updatedAt())
                                    : null
                    );
                })
                .toList();

        return new OrganizationOverviewResponse(departments, positions, employees);
    }

    @Override
    public EmployeeDetailResponse employeeDetail(Long tenantId, Long employeeId) {
        List<AccessUserRow> users = accessControlMapper.selectUsers(tenantId);
        Map<Long, AccessUserRow> userMap = users.stream()
                .collect(Collectors.toMap(AccessUserRow::id, user -> user, (a, b) -> a));

        AccessUserRow user = userMap.get(employeeId);
        if (user == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }

        Map<Long, DepartmentResponse> deptMap = accessControlMapper.selectDepartments(tenantId).stream()
                .collect(Collectors.toMap(DepartmentResponse::id, d -> d, (a, b) -> a));
        Map<Long, PositionResponse> posMap = accessControlMapper.selectPositions(tenantId).stream()
                .collect(Collectors.toMap(PositionResponse::id, p -> p, (a, b) -> a));

        AccessUserRow approver = user.approverUserId() != null ? userMap.get(user.approverUserId()) : null;
        LocalDateTime createdAt = accessControlMapper.selectUserCreatedAt(tenantId, employeeId);

        return new EmployeeDetailResponse(
                user.id(),
                user.name(),
                user.email(),
                user.role(),
                user.status(),
                AvatarUrls.build(user.id(), user.avatar(), user.updatedAt()),
                createdAt,
                user.departmentId(),
                deptName(user.departmentId(), deptMap),
                user.positionId(),
                posName(user.positionId(), posMap),
                user.approverUserId(),
                approver != null ? approver.name() : null,
                approver != null
                        ? AvatarUrls.build(approver.id(), approver.avatar(), approver.updatedAt())
                        : null,
                buildEmploymentHistory(tenantId, employeeId, userMap, deptMap, posMap),
                buildAttendance(tenantId, employeeId),
                buildActivities(tenantId, employeeId)
        );
    }

    private List<EmployeeDetailResponse.EmploymentHistoryRecord> buildEmploymentHistory(
            Long tenantId,
            Long employeeId,
            Map<Long, AccessUserRow> users,
            Map<Long, DepartmentResponse> departments,
            Map<Long, PositionResponse> positions) {
        return employeeChangeMapper.selectList(new LambdaQueryWrapper<EmployeeChange>()
                        .eq(EmployeeChange::getTenantId, tenantId)
                        .eq(EmployeeChange::getEmployeeUserId, employeeId)
                        .eq(EmployeeChange::getStatus, "EFFECTIVE")
                        .orderByDesc(EmployeeChange::getEffectiveDate)
                        .orderByDesc(EmployeeChange::getId))
                .stream()
                .map(change -> new EmployeeDetailResponse.EmploymentHistoryRecord(
                        change.getId(), change.getChangeType(), change.getEffectiveDate(),
                        deptName(change.getCurrentDepartmentId(), departments),
                        posName(change.getCurrentPositionId(), positions),
                        userName(change.getCurrentSupervisorUserId(), users),
                        deptName(change.getTargetDepartmentId(), departments),
                        posName(change.getTargetPositionId(), positions),
                        userName(change.getTargetSupervisorUserId(), users),
                        change.getReason(), change.getAppliedAt()))
                .toList();
    }

    private EmployeeDetailResponse.AttendanceOverview buildAttendance(Long tenantId, Long userId) {
        List<AttendanceRecord> records = attendanceRecordMapper.selectList(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getTenantId, tenantId)
                        .eq(AttendanceRecord::getUserId, userId));

        int normal = 0, late = 0, earlyLeave = 0, lateAndEarly = 0, missing = 0;
        for (AttendanceRecord r : records) {
            String status = r.getStatus() == null ? "" : r.getStatus();
            switch (status) {
                case "NORMAL" -> normal++;
                case "LATE" -> late++;
                case "EARLY_LEAVE" -> earlyLeave++;
                case "LATE_AND_EARLY" -> lateAndEarly++;
                case "MISSING_CLOCK" -> missing++;
                default -> { }
            }
        }
        return new EmployeeDetailResponse.AttendanceOverview(
                records.size(), normal, late, earlyLeave, lateAndEarly, missing);
    }

    private List<EmployeeDetailResponse.ActivityRecord> buildActivities(Long tenantId, Long userId) {
        List<EmployeeDetailResponse.ActivityRecord> records = new ArrayList<>();

        List<LeaveApplication> leaves = leaveApplicationMapper.selectList(
                new LambdaQueryWrapper<LeaveApplication>()
                        .eq(LeaveApplication::getTenantId, tenantId)
                        .eq(LeaveApplication::getApplicantUserId, userId)
                        .orderByDesc(LeaveApplication::getCreatedAt)
                        .last("LIMIT " + RECENT_LIMIT));
        for (LeaveApplication l : leaves) {
            records.add(new EmployeeDetailResponse.ActivityRecord(
                    l.getId(), "LEAVE", l.getLeaveType(), l.getStatus(),
                    l.getStartDate(), l.getEndDate(), l.getCreatedAt()));
        }

        List<AttendanceReissue> reissues = attendanceReissueMapper.selectList(
                new LambdaQueryWrapper<AttendanceReissue>()
                        .eq(AttendanceReissue::getTenantId, tenantId)
                        .eq(AttendanceReissue::getApplicantUserId, userId)
                        .orderByDesc(AttendanceReissue::getCreatedAt)
                        .last("LIMIT " + RECENT_LIMIT));
        for (AttendanceReissue r : reissues) {
            records.add(new EmployeeDetailResponse.ActivityRecord(
                    r.getId(), "REISSUE", r.getClockType(), r.getStatus(),
                    r.getClockDate(), r.getClockDate(), r.getCreatedAt()));
        }

        records.sort(Comparator.comparing(
                EmployeeDetailResponse.ActivityRecord::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return records.stream().limit(ACTIVITY_LIMIT).toList();
    }

    private String deptName(Long id, Map<Long, DepartmentResponse> map) {
        return id != null && map.containsKey(id) ? map.get(id).name() : null;
    }

    private String posName(Long id, Map<Long, PositionResponse> map) {
        return id != null && map.containsKey(id) ? map.get(id).name() : null;
    }

    private String userName(Long id, Map<Long, AccessUserRow> map) {
        return id != null && map.containsKey(id) ? map.get(id).name() : null;
    }
}
