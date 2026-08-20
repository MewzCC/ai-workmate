package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.common.PageResponse;
import com.aiworkmate.dto.AttendanceClockRequest;
import com.aiworkmate.dto.AttendanceClockResponse;
import com.aiworkmate.dto.AttendanceReissueDecisionRequest;
import com.aiworkmate.dto.AttendanceReissueRequest;
import com.aiworkmate.dto.AttendanceReissueResponse;
import com.aiworkmate.dto.AttendanceRecordResponse;
import com.aiworkmate.dto.AttendanceSettingsRequest;
import com.aiworkmate.dto.AttendanceSettingsResponse;
import com.aiworkmate.dto.AttendanceStatisticsResponse;
import com.aiworkmate.dto.AttendanceTodayStatusResponse;
import com.aiworkmate.entity.AttendanceRecord;
import com.aiworkmate.entity.AttendanceReissue;
import com.aiworkmate.entity.AttendanceSetting;
import com.aiworkmate.entity.User;
import com.aiworkmate.mapper.AttendanceRecordMapper;
import com.aiworkmate.mapper.AttendanceReissueMapper;
import com.aiworkmate.mapper.AttendanceSettingMapper;
import com.aiworkmate.mapper.UserMapper;
import com.aiworkmate.service.AttendanceService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考勤管理服务实现。
 *
 * <p>打卡规则（上下班时间与弹性宽限读取 {@code attendance_setting} 配置，
 * 未配置时回落默认 09:00 / 18:00、弹性 0 分钟）：
 * <ul>
 *   <li>上班打卡晚于「上班时间 + 弹性上班宽限」记为迟到；下班打卡早于「预期下班 - 弹性下班宽限」记为早退；
 *       开启「弹性联动下班」时，预期下班随实际上班顺延（9:00 上班 → 18:00 下班；9:30 上班 → 18:30 下班）；</li>
 *   <li>未打上班卡就打下班卡记为 MISSING_CLOCK；</li>
 *   <li>同一天同一类型不可重复打卡。</li>
 * </ul>
 *
 * <p>补卡审批为独立流程（不接入 workflow 体系），由申请人直属上级审批；
 * 审批通过后自动补写打卡记录（视为准时「配置的上班/下班时间」）。
 */
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_TEAM_MEMBERS = 100;

    private final AttendanceRecordMapper recordMapper;
    private final AttendanceReissueMapper reissueMapper;
    private final AttendanceSettingMapper attendanceSettingMapper;
    private final UserMapper userMapper;
    private final UserAccessService userAccessService;

    // ==================== 打卡 ====================

    @Override
    @Transactional
    public AttendanceClockResponse clock(Long userId, AttendanceClockRequest request, String clientIp) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        boolean isClockIn = "CLOCK_IN".equals(request.clockType());
        WorkHours workHours = loadWorkHours(actor.tenantId());

        AttendanceRecord record = findTodayRecord(actor, today);
        if (record == null) {
            if (!isClockIn) {
                // 下班打卡但当天无上班记录 -> 缺卡
                record = newRecord(actor, today);
                record.setClockOutTime(now);
                record.setClockOutIp(clientIp);
                record.setStatus("MISSING_CLOCK");
                record.setLateMinutes(0);
                record.setEarlyLeaveMinutes(0);
                recordMapper.insert(record);
            } else {
                record = newRecord(actor, today);
                record.setClockInTime(now);
                record.setClockInIp(clientIp);
                recomputeStatus(record, workHours);
                recordMapper.insert(record);
            }
        } else {
            if (isClockIn) {
                if (record.getClockInTime() != null) {
                    throw new BusinessException(ErrorCode.ATTENDANCE_ALREADY_CLOCKED);
                }
                record.setClockInTime(now);
                record.setClockInIp(clientIp);
            } else {
                if (record.getClockOutTime() != null) {
                    throw new BusinessException(ErrorCode.ATTENDANCE_ALREADY_CLOCKED);
                }
                record.setClockOutTime(now);
                record.setClockOutIp(clientIp);
            }
            record.setUpdatedAt(now);
            recomputeStatus(record, workHours);
            recordMapper.updateById(record);
        }
        return toClockResponse(record);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceTodayStatusResponse getTodayStatus(Long userId) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        LocalDate today = LocalDate.now();
        AttendanceRecord record = findTodayRecord(actor, today);
        if (record == null) {
            return new AttendanceTodayStatusResponse(null, today, null, null, null, 0, 0, null, null, true, false);
        }
        boolean canClockIn = record.getClockInTime() == null;
        boolean canClockOut = record.getClockInTime() != null && record.getClockOutTime() == null;
        return new AttendanceTodayStatusResponse(
                record.getId(), record.getClockDate(), record.getClockInTime(), record.getClockOutTime(),
                record.getStatus(), nullToZero(record.getLateMinutes()), nullToZero(record.getEarlyLeaveMinutes()),
                record.getClockInIp(), record.getClockOutIp(), canClockIn, canClockOut);
    }

    // ==================== 打卡记录 / 异常 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttendanceRecordResponse> listRecords(Long userId, LocalDate from, LocalDate to,
                                                              Long targetUserId, int page, int size) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        Long queryUserId = resolveTargetUser(actor, targetUserId);

        long total = recordMapper.selectCount(buildRecordWrapper(actor, from, to, queryUserId, null));
        List<AttendanceRecord> records = recordMapper.selectList(
                buildRecordWrapper(actor, from, to, queryUserId, null)
                        .orderByDesc(AttendanceRecord::getClockDate)
                        .orderByDesc(AttendanceRecord::getId)
                        .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize));
        return PageResponse.of(toRecordResponseList(records), total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttendanceRecordResponse> listExceptions(Long userId, LocalDate from, LocalDate to,
                                                                 Long targetUserId, int page, int size) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        Long queryUserId = resolveTargetUser(actor, targetUserId);
        Set<String> exceptionStatuses = Set.of("LATE", "EARLY_LEAVE", "LATE_AND_EARLY", "MISSING_CLOCK");

        long total = recordMapper.selectCount(buildRecordWrapper(actor, from, to, queryUserId, exceptionStatuses));
        List<AttendanceRecord> records = recordMapper.selectList(
                buildRecordWrapper(actor, from, to, queryUserId, exceptionStatuses)
                        .orderByDesc(AttendanceRecord::getClockDate)
                        .orderByDesc(AttendanceRecord::getId)
                        .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize));
        return PageResponse.of(toRecordResponseList(records), total, safePage, safeSize);
    }

    // ==================== 补卡申请 ====================

    @Override
    @Transactional
    public AttendanceReissueResponse submitReissue(Long userId, AttendanceReissueRequest request) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        if (request.clockDate().isAfter(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }
        User applicant = userMapper.selectById(actor.userId());
        if (applicant == null || applicant.getStatus() == null || applicant.getStatus() != 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        Long approverId = applicant.getApproverUserId();
        if (approverId == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_APPROVER_MISSING);
        }
        // 同一日期同一类型不可重复提交待审批申请
        Long exists = reissueMapper.selectCount(new LambdaQueryWrapper<AttendanceReissue>()
                .eq(AttendanceReissue::getTenantId, actor.tenantId())
                .eq(AttendanceReissue::getApplicantUserId, actor.userId())
                .eq(AttendanceReissue::getClockDate, request.clockDate())
                .eq(AttendanceReissue::getClockType, request.clockType())
                .eq(AttendanceReissue::getStatus, "PENDING"));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_STATE_INVALID);
        }

        AttendanceReissue reissue = new AttendanceReissue();
        reissue.setTenantId(actor.tenantId());
        reissue.setApplicantUserId(actor.userId());
        reissue.setApproverUserId(approverId);
        reissue.setClockDate(request.clockDate());
        reissue.setClockType(request.clockType());
        reissue.setReason(request.reason());
        reissue.setStatus("PENDING");
        LocalDateTime now = LocalDateTime.now();
        reissue.setSubmittedAt(now);
        reissue.setCreatedAt(now);
        reissue.setUpdatedAt(now);
        reissueMapper.insert(reissue);
        return toReissueResponse(reissue, actor.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttendanceReissueResponse> listMyReissues(Long userId, String status, int page, int size) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        LambdaQueryWrapper<AttendanceReissue> base = new LambdaQueryWrapper<AttendanceReissue>()
                .eq(AttendanceReissue::getTenantId, actor.tenantId())
                .eq(AttendanceReissue::getApplicantUserId, actor.userId())
                .eq(status != null && !status.isBlank(), AttendanceReissue::getStatus, status);
        long total = reissueMapper.selectCount(base);
        List<AttendanceReissue> records = reissueMapper.selectList(base
                .orderByDesc(AttendanceReissue::getCreatedAt)
                .orderByDesc(AttendanceReissue::getId)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize));
        return PageResponse.of(toReissueResponseList(records, actor.userId()), total, safePage, safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AttendanceReissueResponse> listPendingReissues(Long userId, int page, int size) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        LambdaQueryWrapper<AttendanceReissue> base = new LambdaQueryWrapper<AttendanceReissue>()
                .eq(AttendanceReissue::getTenantId, actor.tenantId())
                .eq(AttendanceReissue::getApproverUserId, actor.userId())
                .eq(AttendanceReissue::getStatus, "PENDING");
        long total = reissueMapper.selectCount(base);
        List<AttendanceReissue> records = reissueMapper.selectList(base
                .orderByDesc(AttendanceReissue::getSubmittedAt)
                .orderByDesc(AttendanceReissue::getId)
                .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize));
        return PageResponse.of(toReissueResponseList(records, actor.userId()), total, safePage, safeSize);
    }

    @Override
    @Transactional
    public AttendanceReissueResponse decideReissue(Long userId, Long id, AttendanceReissueDecisionRequest request) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        AttendanceReissue reissue = reissueMapper.selectById(id);
        if (reissue == null || !actor.tenantId().equals(reissue.getTenantId())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_REISSUE_NOT_FOUND);
        }
        if (!actor.userId().equals(reissue.getApproverUserId())) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        if (!"PENDING".equals(reissue.getStatus())) {
            throw new BusinessException(ErrorCode.ATTENDANCE_REISSUE_DECIDED);
        }
        boolean approved = "APPROVED".equals(request.decision());
        reissue.setStatus(approved ? "APPROVED" : "REJECTED");
        reissue.setApproverComment(request.comment());
        reissue.setDecidedAt(LocalDateTime.now());
        reissueMapper.updateById(reissue);

        if (approved) {
            applyReissueToRecord(reissue);
        }
        return toReissueResponse(reissue, actor.userId());
    }

    // ==================== 统计 ====================

    @Override
    @Transactional(readOnly = true)
    public AttendanceStatisticsResponse getStatistics(Long userId, Integer year, Integer month) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        LocalDate today = LocalDate.now();
        int y = year != null ? year : today.getYear();
        int m = month != null ? month : today.getMonthValue();
        YearMonth ym = YearMonth.of(y, m);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        AttendanceStatisticsResponse.PersonalStats personal = buildPersonalStats(actor, start, end);
        List<AttendanceStatisticsResponse.TeamMemberStats> team = isManager(actor)
                ? buildTeamStats(actor, start, end)
                : null;
        return new AttendanceStatisticsResponse(start, end, personal, team);
    }

    // ==================== 上下班时间配置 ====================

    @Override
    @Transactional(readOnly = true)
    public AttendanceSettingsResponse getSettings(Long userId) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        WorkHours wh = loadWorkHours(actor.tenantId());
        AttendanceSetting row = attendanceSettingMapper.selectOne(new LambdaQueryWrapper<AttendanceSetting>()
                .eq(AttendanceSetting::getTenantId, actor.tenantId()));
        return new AttendanceSettingsResponse(actor.tenantId(), wh.start(), wh.end(),
                wh.startFlex(), wh.endFlex(), wh.linked(), row != null ? row.getUpdatedAt() : null);
    }

    @Override
    @Transactional
    public AttendanceSettingsResponse updateSettings(Long userId, AttendanceSettingsRequest request) {
        ResolvedUserAccess actor = requireActiveUser(userId);
        if (request.workStartTime() == null || request.workEndTime() == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.attendance.workStartTime.required");
        }
        if (request.startFlexMinutes() == null || request.endFlexMinutes() == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.attendance.flex.invalid");
        }
        if (request.flexLinked() == null) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.attendance.flexLinked.required");
        }
        if (!request.workStartTime().isBefore(request.workEndTime())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.attendance.workHoursOrder.invalid");
        }
        if (!isManager(actor)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();
        AttendanceSetting row = attendanceSettingMapper.selectOne(new LambdaQueryWrapper<AttendanceSetting>()
                .eq(AttendanceSetting::getTenantId, actor.tenantId()));
        if (row == null) {
            row = new AttendanceSetting();
            row.setTenantId(actor.tenantId());
            row.setCreatedAt(now);
        }
        row.setWorkStartTime(request.workStartTime());
        row.setWorkEndTime(request.workEndTime());
        row.setStartFlexMinutes(request.startFlexMinutes());
        row.setEndFlexMinutes(request.endFlexMinutes());
        row.setFlexLinked(request.flexLinked());
        row.setUpdatedBy(actor.userId());
        row.setUpdatedAt(now);
        if (row.getId() == null) {
            attendanceSettingMapper.insert(row);
        } else {
            attendanceSettingMapper.updateById(row);
        }
        return new AttendanceSettingsResponse(actor.tenantId(), row.getWorkStartTime(), row.getWorkEndTime(),
                nullToZero(row.getStartFlexMinutes()), nullToZero(row.getEndFlexMinutes()),
                Boolean.TRUE.equals(row.getFlexLinked()), row.getUpdatedAt());
    }

    // ==================== 辅助方法 ====================

    private ResolvedUserAccess requireActiveUser(Long userId) {
        ResolvedUserAccess access = userAccessService.resolveActiveUser(userId);
        if (access == null) {
            throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        }
        return access;
    }

    private boolean isManager(ResolvedUserAccess actor) {
        return "SUPER_ADMIN".equals(actor.role()) || "SYSTEM_ADMIN".equals(actor.role());
    }

    private AttendanceRecord findTodayRecord(ResolvedUserAccess actor, LocalDate today) {
        return recordMapper.selectOne(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getTenantId, actor.tenantId())
                .eq(AttendanceRecord::getUserId, actor.userId())
                .eq(AttendanceRecord::getClockDate, today));
    }

    private AttendanceRecord newRecord(ResolvedUserAccess actor, LocalDate today) {
        LocalDateTime now = LocalDateTime.now();
        AttendanceRecord record = new AttendanceRecord();
        record.setTenantId(actor.tenantId());
        record.setUserId(actor.userId());
        record.setClockDate(today);
        record.setSource("WEB");
        record.setLateMinutes(0);
        record.setEarlyLeaveMinutes(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        return record;
    }

    /** 根据当前打卡时间重新计算 status / late_minutes / early_leave_minutes（按配置的上下班时间与弹性宽限）。 */
    private void recomputeStatus(AttendanceRecord record, WorkHours wh) {
        LocalDateTime clockIn = record.getClockInTime();
        LocalDateTime clockOut = record.getClockOutTime();

        if (clockIn == null && clockOut == null) {
            record.setStatus("NORMAL");
            record.setLateMinutes(0);
            record.setEarlyLeaveMinutes(0);
            return;
        }
        if (clockIn == null) {
            record.setStatus("MISSING_CLOCK");
            record.setLateMinutes(0);
            record.setEarlyLeaveMinutes(0);
            return;
        }
        // 弹性宽限：上班晚于「上班时间 + 弹性上班宽限」才迟到；早于「预期下班 - 弹性下班宽限」才早退
        LocalTime flexStart = wh.start().plusMinutes(wh.startFlex());
        LocalTime referenceOut = expectedClockOut(clockIn.toLocalTime(), wh);
        LocalTime flexEnd = referenceOut.minusMinutes(wh.endFlex());
        boolean late = clockIn.toLocalTime().isAfter(flexStart);
        int lateMinutes = late
                ? maxZero(Duration.between(LocalDateTime.of(record.getClockDate(), flexStart), clockIn).toMinutes())
                : 0;
        int earlyMinutes = 0;
        boolean early = false;
        if (clockOut != null) {
            early = clockOut.toLocalTime().isBefore(flexEnd);
            earlyMinutes = early
                    ? maxZero(Duration.between(clockOut, LocalDateTime.of(record.getClockDate(), flexEnd)).toMinutes())
                    : 0;
        }
        String status;
        if (late && early) {
            status = "LATE_AND_EARLY";
        } else if (late) {
            status = "LATE";
        } else if (early) {
            status = "EARLY_LEAVE";
        } else {
            status = "NORMAL";
        }
        record.setStatus(status);
        record.setLateMinutes(lateMinutes);
        record.setEarlyLeaveMinutes(earlyMinutes);
    }

    /**
     * 预期下班时间：开启弹性联动后，按下班 = max(实际打卡时间, 标准上班时间) + 工时
     * （9:00 上班 → 18:00 下班；9:30 上班 → 18:30 下班）；关闭联动则返回标准下班时间。
     */
    private LocalTime expectedClockOut(LocalTime clockIn, WorkHours wh) {
        if (!wh.linked()) {
            return wh.end();
        }
        long durationMinutes = Duration.between(wh.start(), wh.end()).toMinutes();
        LocalTime arrivalBase = clockIn.isBefore(wh.start()) ? wh.start() : clockIn;
        return arrivalBase.plusMinutes((int) durationMinutes);
    }

    /** 补卡审批通过后，把补卡信息写回打卡记录（视为准时「配置的上班/下班时间」）。 */
    private void applyReissueToRecord(AttendanceReissue reissue) {
        WorkHours wh = loadWorkHours(reissue.getTenantId());
        AttendanceRecord record = recordMapper.selectOne(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getTenantId, reissue.getTenantId())
                .eq(AttendanceRecord::getUserId, reissue.getApplicantUserId())
                .eq(AttendanceRecord::getClockDate, reissue.getClockDate()));
        boolean isClockIn = "CLOCK_IN".equals(reissue.getClockType());
        if (record == null) {
            LocalDateTime now = LocalDateTime.now();
            record = new AttendanceRecord();
            record.setTenantId(reissue.getTenantId());
            record.setUserId(reissue.getApplicantUserId());
            record.setClockDate(reissue.getClockDate());
            record.setSource("WEB");
            record.setLateMinutes(0);
            record.setEarlyLeaveMinutes(0);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
        } else {
            record.setUpdatedAt(LocalDateTime.now());
        }
        if (isClockIn) {
            record.setClockInTime(LocalDateTime.of(reissue.getClockDate(), wh.start()));
            record.setClockInIp("REISSUE");
        } else {
            record.setClockOutTime(LocalDateTime.of(reissue.getClockDate(), wh.end()));
            record.setClockOutIp("REISSUE");
        }
        recomputeStatus(record, wh);
        if (record.getId() == null) {
            recordMapper.insert(record);
        } else {
            recordMapper.updateById(record);
        }
    }

    /** 读取租户的上下班时间配置；未配置时回落默认 09:00 / 18:00、弹性 0 分钟。 */
    private WorkHours loadWorkHours(Long tenantId) {
        AttendanceSetting setting = attendanceSettingMapper.selectOne(new LambdaQueryWrapper<AttendanceSetting>()
                .eq(AttendanceSetting::getTenantId, tenantId));
        if (setting == null) {
            return WorkHours.DEFAULTS;
        }
        return new WorkHours(
                setting.getWorkStartTime() != null ? setting.getWorkStartTime() : WORK_START,
                setting.getWorkEndTime() != null ? setting.getWorkEndTime() : WORK_END,
                nullToZero(setting.getStartFlexMinutes()),
                nullToZero(setting.getEndFlexMinutes()),
                Boolean.TRUE.equals(setting.getFlexLinked()));
    }

    private LambdaQueryWrapper<AttendanceRecord> buildRecordWrapper(ResolvedUserAccess actor,
                                                                    LocalDate from, LocalDate to,
                                                                    Long queryUserId,
                                                                    Set<String> statusSet) {
        LambdaQueryWrapper<AttendanceRecord> wrapper = new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getTenantId, actor.tenantId())
                .eq(queryUserId != null, AttendanceRecord::getUserId, queryUserId)
                .ge(from != null, AttendanceRecord::getClockDate, from)
                .le(to != null, AttendanceRecord::getClockDate, to);
        if (statusSet != null && !statusSet.isEmpty()) {
            wrapper.in(AttendanceRecord::getStatus, statusSet);
        }
        return wrapper;
    }

    /** 普通员工只能看自己；管理员可指定他人或全员。 */
    private Long resolveTargetUser(ResolvedUserAccess actor, Long targetUserId) {
        if (!isManager(actor)) {
            return actor.userId();
        }
        return targetUserId;
    }

    private List<AttendanceRecordResponse> toRecordResponseList(List<AttendanceRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = records.stream().map(AttendanceRecord::getUserId).collect(Collectors.toSet());
        Map<Long, String> nameMap = loadUserNames(userIds);
        return records.stream()
                .map(r -> new AttendanceRecordResponse(r.getId(), r.getUserId(),
                        nameMap.get(r.getUserId()), r.getClockDate(), r.getClockInTime(), r.getClockOutTime(),
                        r.getStatus(), nullToZero(r.getLateMinutes()), nullToZero(r.getEarlyLeaveMinutes())))
                .toList();
    }

    private List<AttendanceReissueResponse> toReissueResponseList(List<AttendanceReissue> records, Long currentUserId) {
        if (records.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        records.forEach(r -> {
            userIds.add(r.getApplicantUserId());
            if (r.getApproverUserId() != null) {
                userIds.add(r.getApproverUserId());
            }
        });
        Map<Long, String> nameMap = loadUserNames(userIds);
        return records.stream().map(r -> toReissueResponse(r, nameMap, currentUserId)).toList();
    }

    private AttendanceReissueResponse toReissueResponse(AttendanceReissue reissue, Long currentUserId) {
        Set<Long> userIds = new HashSet<>();
        userIds.add(reissue.getApplicantUserId());
        if (reissue.getApproverUserId() != null) {
            userIds.add(reissue.getApproverUserId());
        }
        Map<Long, String> nameMap = loadUserNames(userIds);
        return toReissueResponse(reissue, nameMap, currentUserId);
    }

    private AttendanceReissueResponse toReissueResponse(AttendanceReissue reissue, Map<Long, String> nameMap,
                                                        Long currentUserId) {
        boolean canDecide = currentUserId.equals(reissue.getApproverUserId()) && "PENDING".equals(reissue.getStatus());
        boolean canWithdraw = currentUserId.equals(reissue.getApplicantUserId()) && "PENDING".equals(reissue.getStatus());
        return new AttendanceReissueResponse(
                reissue.getId(), reissue.getApplicantUserId(), nameMap.get(reissue.getApplicantUserId()),
                reissue.getApproverUserId(),
                reissue.getApproverUserId() != null ? nameMap.get(reissue.getApproverUserId()) : null,
                reissue.getClockDate(), reissue.getClockType(), reissue.getReason(), reissue.getStatus(),
                reissue.getApproverComment(), reissue.getSubmittedAt(), reissue.getDecidedAt(),
                reissue.getCreatedAt(), reissue.getUpdatedAt(), canDecide, canWithdraw);
    }

    private AttendanceClockResponse toClockResponse(AttendanceRecord record) {
        return new AttendanceClockResponse(record.getId(), record.getClockDate(),
                record.getClockInTime(), record.getClockOutTime(), record.getStatus(),
                nullToZero(record.getLateMinutes()), nullToZero(record.getEarlyLeaveMinutes()));
    }

    private Map<Long, String> loadUserNames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> map = new HashMap<>();
        for (User u : users) {
            String name = u.getDisplayName() != null ? u.getDisplayName() : u.getUsername();
            map.put(u.getId(), name);
        }
        return map;
    }

    private AttendanceStatisticsResponse.PersonalStats buildPersonalStats(ResolvedUserAccess actor,
                                                                         LocalDate start, LocalDate end) {
        List<AttendanceRecord> records = recordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getTenantId, actor.tenantId())
                .eq(AttendanceRecord::getUserId, actor.userId())
                .between(AttendanceRecord::getClockDate, start, end));
        long pending = reissueMapper.selectCount(new LambdaQueryWrapper<AttendanceReissue>()
                .eq(AttendanceReissue::getTenantId, actor.tenantId())
                .eq(AttendanceReissue::getApplicantUserId, actor.userId())
                .eq(AttendanceReissue::getStatus, "PENDING"));
        Map<Long, String> nameMap = loadUserNames(Set.of(actor.userId()));
        return aggregatePersonal(records, actor.userId(), nameMap.get(actor.userId()), (int) pending);
    }

    private List<AttendanceStatisticsResponse.TeamMemberStats> buildTeamStats(ResolvedUserAccess actor,
                                                                              LocalDate start, LocalDate end) {
        List<User> users = userMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getTenantId, actor.tenantId())
                .eq(User::getStatus, 1)
                .orderByAsc(User::getId)
                .last("LIMIT " + MAX_TEAM_MEMBERS));
        if (users.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        List<AttendanceRecord> records = recordMapper.selectList(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getTenantId, actor.tenantId())
                .in(AttendanceRecord::getUserId, userIds)
                .between(AttendanceRecord::getClockDate, start, end));
        Map<Long, List<AttendanceRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(AttendanceRecord::getUserId));
        List<AttendanceStatisticsResponse.TeamMemberStats> result = new ArrayList<>(users.size());
        for (User u : users) {
            List<AttendanceRecord> userRecords = grouped.getOrDefault(u.getId(), List.of());
            AttendanceStatisticsResponse.PersonalStats stats = aggregatePersonal(userRecords, u.getId(),
                    u.getDisplayName() != null ? u.getDisplayName() : u.getUsername(), 0);
            result.add(new AttendanceStatisticsResponse.TeamMemberStats(
                    stats.userId(), stats.userName(), null,
                    stats.totalDays(), stats.normalDays(), stats.lateDays(),
                    stats.earlyLeaveDays(), stats.missingDays()));
        }
        return result;
    }

    private AttendanceStatisticsResponse.PersonalStats aggregatePersonal(List<AttendanceRecord> records,
                                                                         Long userId, String userName,
                                                                         int pendingReissueCount) {
        int total = records.size();
        int normal = 0, late = 0, early = 0, missing = 0;
        for (AttendanceRecord r : records) {
            switch (r.getStatus()) {
                case "NORMAL" -> normal++;
                case "LATE" -> late++;
                case "EARLY_LEAVE" -> early++;
                case "LATE_AND_EARLY" -> { late++; early++; }
                case "MISSING_CLOCK" -> missing++;
                default -> { /* ignore */ }
            }
        }
        return new AttendanceStatisticsResponse.PersonalStats(userId, userName, total, normal, late, early, missing,
                pendingReissueCount);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private int maxZero(long value) {
        return (int) Math.max(0L, value);
    }

    /** 租户上下班时间、弹性宽限与联动开关；缺省 09:00 / 18:00、宽限 0、不联动。 */
    private record WorkHours(LocalTime start, LocalTime end, int startFlex, int endFlex, boolean linked) {
        static final WorkHours DEFAULTS = new WorkHours(WORK_START, WORK_END, 0, 0, false);
    }
}
