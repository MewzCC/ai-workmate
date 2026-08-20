package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.AttendanceClockRequest;
import com.aiworkmate.dto.AttendanceClockResponse;
import com.aiworkmate.dto.AttendanceSettingsRequest;
import com.aiworkmate.dto.AttendanceSettingsResponse;
import com.aiworkmate.entity.AttendanceRecord;
import com.aiworkmate.entity.AttendanceSetting;
import com.aiworkmate.mapper.AttendanceRecordMapper;
import com.aiworkmate.mapper.AttendanceReissueMapper;
import com.aiworkmate.mapper.AttendanceSettingMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 考勤打卡服务回归测试。
 *
 * <p>打卡写入 {@code attendance_record} / {@code attendance_reissue} 时，
 * {@code created_at} / {@code updated_at} 必须显式赋值：实体上的
 * {@code @TableField(fill = ...)} 在没有 {@code MetaObjectHandler} 时会无条件
 * 把这两列拼进 INSERT/UPDATE 语句，若为 null 会触发 PostgreSQL NOT NULL 约束
 * （表现为 /api/attendance/clock 返回 500）。
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    private static final long USER_ID = 1001L;

    private static final ResolvedUserAccess ACTOR = new ResolvedUserAccess(
            USER_ID, "alice", "EMPLOYEE", List.of("route:attendance-clock"));

    @Mock
    private AttendanceRecordMapper recordMapper;

    @Mock
    private AttendanceReissueMapper reissueMapper;

    @Mock
    private AttendanceSettingMapper attendanceSettingMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserAccessService userAccessService;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    @Test
    void clockIn_shouldInsertRecordWithTimestamps() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(ACTOR);
        when(recordMapper.selectOne(any())).thenReturn(null);
        // 设定 23:59:59.999999999 为上班/下班时间，使任何时刻打卡都不算迟到，
        // 避免测试对执行时间敏感（默认 09:00 上班时，UTC 09:05 执行会被判 LATE）。
        AttendanceSetting lateShift = new AttendanceSetting();
        lateShift.setWorkStartTime(LocalTime.MAX);
        lateShift.setWorkEndTime(LocalTime.MAX);
        lateShift.setStartFlexMinutes(0);
        lateShift.setEndFlexMinutes(0);
        lateShift.setFlexLinked(false);
        when(attendanceSettingMapper.selectOne(any())).thenReturn(lateShift);

        AttendanceClockResponse response =
                attendanceService.clock(USER_ID, new AttendanceClockRequest("CLOCK_IN"), "127.0.0.1");

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(recordMapper).insert(captor.capture());
        AttendanceRecord inserted = captor.getValue();

        assertThat(response.status()).isEqualTo("NORMAL");
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.getUpdatedAt()).isNotNull();
        assertThat(inserted.getClockInTime()).isNotNull();
    }

    @Test
    void clockOutWithoutClockIn_shouldInsertMissingClockRecordWithTimestamps() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(ACTOR);
        when(recordMapper.selectOne(any())).thenReturn(null);

        attendanceService.clock(USER_ID, new AttendanceClockRequest("CLOCK_OUT"), "127.0.0.1");

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(recordMapper).insert(captor.capture());
        AttendanceRecord inserted = captor.getValue();

        assertThat(inserted.getStatus()).isEqualTo("MISSING_CLOCK");
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.getUpdatedAt()).isNotNull();
    }

    @Test
    void clockOutOnExistingRecord_shouldUpdateAndRefreshUpdatedAt() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(ACTOR);
        AttendanceRecord existing = recordWithClockIn();
        when(recordMapper.selectOne(any())).thenReturn(existing);

        attendanceService.clock(USER_ID, new AttendanceClockRequest("CLOCK_OUT"), "127.0.0.1");

        ArgumentCaptor<AttendanceRecord> captor = ArgumentCaptor.forClass(AttendanceRecord.class);
        verify(recordMapper).updateById(captor.capture());
        AttendanceRecord updated = captor.getValue();

        assertThat(updated.getClockOutTime()).isNotNull();
        assertThat(updated.getUpdatedAt()).isNotNull();
    }

    @Test
    void duplicateClockIn_shouldThrowAlreadyClocked() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(ACTOR);
        when(recordMapper.selectOne(any())).thenReturn(recordWithClockIn());

        assertThatThrownBy(() -> attendanceService.clock(
                USER_ID, new AttendanceClockRequest("CLOCK_IN"), "127.0.0.1"))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.ATTENDANCE_ALREADY_CLOCKED.getErrorCode()));
    }

    @Test
    void getSettings_shouldReturnDefaultsWhenNotConfigured() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(ACTOR);
        when(attendanceSettingMapper.selectOne(any())).thenReturn(null);

        AttendanceSettingsResponse response = attendanceService.getSettings(USER_ID);

        assertThat(response.workStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.workEndTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(response.startFlexMinutes()).isZero();
        assertThat(response.endFlexMinutes()).isZero();
        assertThat(response.flexLinked()).isFalse();
    }

    @Test
    void updateSettings_shouldRejectNonManager() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(
                new ResolvedUserAccess(USER_ID, "alice", "EMPLOYEE", List.of("route:attendance-clock")));

        assertThatThrownBy(() -> attendanceService.updateSettings(USER_ID,
                new AttendanceSettingsRequest(LocalTime.of(8, 30), LocalTime.of(17, 30), 30, 15, true)))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.PERMISSION_DENIED.getErrorCode()));
    }

    @Test
    void updateSettings_shouldRejectEndNotAfterStart() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(
                new ResolvedUserAccess(USER_ID, "admin", "SYSTEM_ADMIN", List.of("route:attendance-clock")));

        assertThatThrownBy(() -> attendanceService.updateSettings(USER_ID,
                new AttendanceSettingsRequest(LocalTime.of(9, 0), LocalTime.of(9, 0), 0, 0, false)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void updateSettings_shouldInsertConfiguredSettingsForTenant() {
        when(userAccessService.resolveActiveUser(USER_ID)).thenReturn(
                new ResolvedUserAccess(USER_ID, "admin", "SYSTEM_ADMIN", List.of("route:attendance-clock")));
        when(attendanceSettingMapper.selectOne(any())).thenReturn(null);

        AttendanceSettingsResponse response = attendanceService.updateSettings(USER_ID,
                new AttendanceSettingsRequest(LocalTime.of(8, 30), LocalTime.of(17, 30), 30, 15, true));

        ArgumentCaptor<AttendanceSetting> captor =
                ArgumentCaptor.forClass(AttendanceSetting.class);
        verify(attendanceSettingMapper).insert(captor.capture());
        AttendanceSetting inserted = captor.getValue();

        assertThat(inserted.getTenantId()).isEqualTo(1L);
        assertThat(inserted.getWorkStartTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(inserted.getWorkEndTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(inserted.getStartFlexMinutes()).isEqualTo(30);
        assertThat(inserted.getEndFlexMinutes()).isEqualTo(15);
        assertThat(inserted.getFlexLinked()).isTrue();
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.getUpdatedAt()).isNotNull();
        assertThat(response.workStartTime()).isEqualTo(LocalTime.of(8, 30));
        assertThat(response.startFlexMinutes()).isEqualTo(30);
        assertThat(response.flexLinked()).isTrue();
    }

    private AttendanceRecord recordWithClockIn() {
        AttendanceRecord record = new AttendanceRecord();
        record.setId(1L);
        record.setTenantId(1L);
        record.setUserId(USER_ID);
        record.setClockDate(LocalDate.now());
        record.setClockInTime(java.time.LocalDateTime.now());
        record.setStatus("NORMAL");
        record.setLateMinutes(0);
        record.setEarlyLeaveMinutes(0);
        record.setSource("WEB");
        record.setCreatedAt(java.time.LocalDateTime.now());
        record.setUpdatedAt(java.time.LocalDateTime.now());
        return record;
    }
}
