package com.aiworkmate.service.impl;

import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.EmployeeDetailResponse;
import com.aiworkmate.dto.PositionResponse;
import com.aiworkmate.entity.EmployeeChange;
import com.aiworkmate.mapper.AccessControlMapper;
import com.aiworkmate.mapper.AttendanceRecordMapper;
import com.aiworkmate.mapper.AttendanceReissueMapper;
import com.aiworkmate.mapper.EmployeeChangeMapper;
import com.aiworkmate.mapper.LeaveApplicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrServiceImplTest {
    @Mock private AccessControlMapper accessControlMapper;
    @Mock private AttendanceRecordMapper attendanceRecordMapper;
    @Mock private AttendanceReissueMapper attendanceReissueMapper;
    @Mock private LeaveApplicationMapper leaveApplicationMapper;
    @Mock private EmployeeChangeMapper employeeChangeMapper;

    private HrServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HrServiceImpl(accessControlMapper, attendanceRecordMapper,
                attendanceReissueMapper, leaveApplicationMapper, employeeChangeMapper);
    }

    @Test
    void employeeDetailIncludesEffectiveEmploymentHistoryWithOrganizationNames() {
        LocalDateTime now = LocalDateTime.now();
        when(accessControlMapper.selectUsers(1L)).thenReturn(List.of(
                new AccessUserRow(1001L, "员工甲", "employee@example.com", "EMPLOYEE", 1,
                        1L, 20L, 30L, 2002L, 1L, now, null),
                new AccessUserRow(2002L, "主管乙", "manager@example.com", "SYSTEM_ADMIN", 1,
                        1L, 20L, 31L, null, 1L, now, null)));
        when(accessControlMapper.selectDepartments(1L)).thenReturn(List.of(
                new DepartmentResponse(10L, "OLD", "原部门", null, null, 1),
                new DepartmentResponse(20L, "NEW", "新部门", null, null, 1)));
        when(accessControlMapper.selectPositions(1L)).thenReturn(List.of(
                new PositionResponse(11L, "OLD", "原岗位", 1),
                new PositionResponse(30L, "NEW", "新岗位", 1),
                new PositionResponse(31L, "MANAGER", "主管", 1)));
        when(accessControlMapper.selectUserCreatedAt(1L, 1001L)).thenReturn(now.minusYears(1));

        EmployeeChange change = new EmployeeChange();
        change.setId(9L);
        change.setChangeType("TRANSFER");
        change.setEffectiveDate(LocalDate.now().minusDays(1));
        change.setCurrentDepartmentId(10L);
        change.setCurrentPositionId(11L);
        change.setTargetDepartmentId(20L);
        change.setTargetPositionId(30L);
        change.setTargetSupervisorUserId(2002L);
        change.setReason("组织调整");
        change.setAppliedAt(now.minusDays(1));
        when(employeeChangeMapper.selectList(any())).thenReturn(List.of(change));
        when(attendanceRecordMapper.selectList(any())).thenReturn(List.of());
        when(leaveApplicationMapper.selectList(any())).thenReturn(List.of());
        when(attendanceReissueMapper.selectList(any())).thenReturn(List.of());

        EmployeeDetailResponse response = service.employeeDetail(1L, 1001L);

        assertThat(response.departmentName()).isEqualTo("新部门");
        assertThat(response.positionName()).isEqualTo("新岗位");
        assertThat(response.approverName()).isEqualTo("主管乙");
        assertThat(response.employmentHistory()).singleElement().satisfies(history -> {
            assertThat(history.changeType()).isEqualTo("TRANSFER");
            assertThat(history.currentDepartmentName()).isEqualTo("原部门");
            assertThat(history.targetDepartmentName()).isEqualTo("新部门");
            assertThat(history.targetSupervisorName()).isEqualTo("主管乙");
        });
    }
}
