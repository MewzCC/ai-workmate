package com.aiworkmate.mapper;

import com.aiworkmate.dto.LeaveApplicationView;
import com.aiworkmate.entity.LeaveApplication;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LeaveApplicationMapper extends BaseMapper<LeaveApplication> {

    @Select("""
            SELECT l.id,
                   l.applicant_user_id AS applicantUserId,
                   COALESCE(NULLIF(applicant.display_name, ''), applicant.username) AS applicantName,
                   l.approver_user_id AS approverUserId,
                   COALESCE(NULLIF(approver.display_name, ''), approver.username) AS approverName,
                   l.leave_type AS leaveType,
                   l.start_date AS startDate,
                   l.start_period AS startPeriod,
                   l.end_date AS endDate,
                   l.end_period AS endPeriod,
                   l.duration_half_days AS durationHalfDays,
                   l.reason,
                   l.status,
                   l.version,
                   task.id AS taskId,
                   task.version AS taskVersion,
                   l.submitted_at AS submittedAt,
                   l.completed_at AS completedAt,
                   l.created_at AS createdAt,
                   l.updated_at AS updatedAt
            FROM leave_application l
            JOIN app_user applicant ON applicant.id = l.applicant_user_id
            LEFT JOIN app_user approver ON approver.id = l.approver_user_id
            LEFT JOIN LATERAL (
                SELECT wt.id, wt.version
                FROM workflow_task wt
                WHERE wt.tenant_id = l.tenant_id
                  AND wt.business_type = 'LEAVE_APPLICATION'
                  AND wt.business_id = l.id
                ORDER BY wt.created_at DESC, wt.id DESC
                LIMIT 1
            ) task ON TRUE
            WHERE l.tenant_id = #{tenantId} AND l.id = #{id}
            """)
    LeaveApplicationView selectView(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select({
            "<script>",
            "SELECT l.id, l.applicant_user_id AS applicantUserId,",
            "COALESCE(NULLIF(applicant.display_name, ''), applicant.username) AS applicantName,",
            "l.approver_user_id AS approverUserId,",
            "COALESCE(NULLIF(approver.display_name, ''), approver.username) AS approverName,",
            "l.leave_type AS leaveType, l.start_date AS startDate, l.start_period AS startPeriod,",
            "l.end_date AS endDate, l.end_period AS endPeriod,",
            "l.duration_half_days AS durationHalfDays, l.reason, l.status, l.version,",
            "NULL AS taskId, NULL AS taskVersion, l.submitted_at AS submittedAt,",
            "l.completed_at AS completedAt, l.created_at AS createdAt, l.updated_at AS updatedAt",
            "FROM leave_application l",
            "JOIN app_user applicant ON applicant.id = l.applicant_user_id",
            "LEFT JOIN app_user approver ON approver.id = l.approver_user_id",
            "WHERE l.tenant_id = #{tenantId} AND l.applicant_user_id = #{userId}",
            "<if test='status != null and status != \"\"'> AND l.status = #{status}</if>",
            "ORDER BY l.created_at DESC, l.id DESC",
            "LIMIT #{size} OFFSET #{offset}",
            "</script>"
    })
    List<LeaveApplicationView> selectMine(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM leave_application",
            "WHERE tenant_id = #{tenantId} AND applicant_user_id = #{userId}",
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "</script>"
    })
    long countMine(@Param("tenantId") Long tenantId,
                   @Param("userId") Long userId,
                   @Param("status") String status);

    @Select("""
            SELECT candidate_id
            FROM (
                SELECT direct.id AS candidate_id, 1 AS priority
                FROM app_user applicant
                JOIN app_user direct
                  ON direct.id = applicant.approver_user_id
                 AND direct.tenant_id = applicant.tenant_id
                 AND direct.status = 1
                WHERE applicant.id = #{applicantId}
                  AND applicant.tenant_id = #{tenantId}
                  AND direct.id <> applicant.id
                  AND (
                    EXISTS (
                        SELECT 1
                        FROM user_role ur
                        JOIN rbac_role_permission rp
                          ON rp.tenant_id = ur.tenant_id
                         AND rp.role_code = ur.role_code
                         AND rp.permission_code = 'approval:act'
                        WHERE ur.tenant_id = #{tenantId} AND ur.user_id = direct.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM user_role ur
                        WHERE ur.tenant_id = #{tenantId}
                          AND ur.user_id = direct.id
                          AND ur.role_code = 'SUPER_ADMIN'
                    )
                  )
                UNION ALL
                SELECT fallback_user.id AS candidate_id, 2 AS priority
                FROM app_user applicant
                JOIN department d
                  ON d.id = applicant.department_id
                 AND d.tenant_id = applicant.tenant_id
                 AND d.status = 1
                JOIN app_user fallback_user
                  ON fallback_user.id = d.default_approver_user_id
                 AND fallback_user.tenant_id = applicant.tenant_id
                 AND fallback_user.status = 1
                WHERE applicant.id = #{applicantId}
                  AND applicant.tenant_id = #{tenantId}
                  AND fallback_user.id <> applicant.id
                  AND (
                    EXISTS (
                        SELECT 1
                        FROM user_role ur
                        JOIN rbac_role_permission rp
                          ON rp.tenant_id = ur.tenant_id
                         AND rp.role_code = ur.role_code
                         AND rp.permission_code = 'approval:act'
                        WHERE ur.tenant_id = #{tenantId} AND ur.user_id = fallback_user.id
                    )
                    OR EXISTS (
                        SELECT 1 FROM user_role ur
                        WHERE ur.tenant_id = #{tenantId}
                          AND ur.user_id = fallback_user.id
                          AND ur.role_code = 'SUPER_ADMIN'
                    )
                  )
            ) candidates
            ORDER BY priority
            LIMIT 1
            """)
    Long resolveApprover(@Param("tenantId") Long tenantId,
                         @Param("applicantId") Long applicantId);

    @Select("""
            SELECT id FROM workflow_definition
            WHERE tenant_id = #{tenantId}
              AND code = 'LEAVE_SINGLE_APPROVAL'
              AND enabled = TRUE
            ORDER BY version DESC
            LIMIT 1
            """)
    Long selectLeaveDefinitionId(Long tenantId);
}
