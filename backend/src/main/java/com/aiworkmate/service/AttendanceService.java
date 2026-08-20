package com.aiworkmate.service;

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

import java.time.LocalDate;

/**
 * 考勤管理服务。
 *
 * <p>覆盖打卡、打卡记录查询、异常考勤查询、补卡申请与独立审批、考勤统计。
 * 所有接口按当前认证 {@code userId} 解析租户与归属，普通员工仅能查看自己的数据，
 * {@code SUPER_ADMIN}/{@code SYSTEM_ADMIN} 可查看全员。
 */
public interface AttendanceService {

    /** 打卡（上班/下班）。{@code clientIp} 由 Controller 从请求头解析。 */
    AttendanceClockResponse clock(Long userId, AttendanceClockRequest request, String clientIp);

    /** 今日打卡状态（未打卡也返回，前端据此控制按钮可用性）。 */
    AttendanceTodayStatusResponse getTodayStatus(Long userId);

    /** 打卡记录分页查询。普通员工仅看自己；管理员可指定 {@code targetUserId} 看他人或全员。 */
    PageResponse<AttendanceRecordResponse> listRecords(Long userId, LocalDate from, LocalDate to,
                                                       Long targetUserId, int page, int size);

    /** 异常考勤分页查询（status != NORMAL）。权限模型同 {@link #listRecords}。 */
    PageResponse<AttendanceRecordResponse> listExceptions(Long userId, LocalDate from, LocalDate to,
                                                          Long targetUserId, int page, int size);

    /** 提交补卡申请。审批人取申请人直属上级 {@code approver_user_id}。 */
    AttendanceReissueResponse submitReissue(Long userId, AttendanceReissueRequest request);

    /** 我的补卡申请列表。 */
    PageResponse<AttendanceReissueResponse> listMyReissues(Long userId, String status, int page, int size);

    /** 待我审批的补卡申请列表。 */
    PageResponse<AttendanceReissueResponse> listPendingReissues(Long userId, int page, int size);

    /** 审批补卡申请。仅审批人可操作；通过后自动补写打卡记录。 */
    AttendanceReissueResponse decideReissue(Long userId, Long id, AttendanceReissueDecisionRequest request);

    /** 考勤统计。普通员工返回个人统计；管理员额外返回团队成员统计。 */
    AttendanceStatisticsResponse getStatistics(Long userId, Integer year, Integer month);

    /** 读取当前租户的上下班时间配置（不存在时返回默认 09:00 / 18:00、弹性 0 分钟）。 */
    AttendanceSettingsResponse getSettings(Long userId);

    /** 更新当前租户的上下班时间配置。仅 {@code SUPER_ADMIN}/{@code SYSTEM_ADMIN} 可操作。 */
    AttendanceSettingsResponse updateSettings(Long userId, AttendanceSettingsRequest request);
}
