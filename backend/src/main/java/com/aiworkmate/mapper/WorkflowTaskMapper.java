package com.aiworkmate.mapper;

import com.aiworkmate.dto.TodoResponse;
import com.aiworkmate.entity.WorkflowTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface WorkflowTaskMapper extends BaseMapper<WorkflowTask> {

    @Select({
            "<script>",
            "SELECT wt.id, wt.business_id AS applicationId,",
            "l.applicant_user_id AS applicantUserId,",
            "COALESCE(NULLIF(u.display_name, ''), u.username) AS applicantName,",
            "l.leave_type AS leaveType, l.duration_half_days AS durationHalfDays,",
            "wt.status, wt.version, l.submitted_at AS submittedAt, wt.due_at AS dueAt,",
            "(wt.status = 'PENDING' AND wt.due_at IS NOT NULL AND wt.due_at &lt; CURRENT_TIMESTAMP) AS overdue,",
            "u.avatar AS applicantAvatar,",
            "u.updated_at AS applicantUpdatedAt,",
            "NULL AS applicantAvatarUrl",
            "FROM workflow_task wt",
            "JOIN leave_application l ON l.tenant_id = wt.tenant_id AND l.id = wt.business_id",
            "JOIN app_user u ON u.id = l.applicant_user_id",
            "WHERE wt.tenant_id = #{tenantId} AND wt.assignee_user_id = #{assigneeId}",
            "AND wt.business_type = 'LEAVE_APPLICATION'",
            "<if test='status != null and status != \"\"'> AND wt.status = #{status}</if>",
            "<if test='from != null'> AND wt.created_at &gt;= #{from}</if>",
            "<if test='to != null'> AND wt.created_at &lt;= #{to}</if>",
            "ORDER BY CASE WHEN wt.status = 'PENDING' THEN 0 ELSE 1 END, wt.created_at DESC, wt.id DESC",
            "LIMIT #{size} OFFSET #{offset}",
            "</script>"
    })
    List<TodoResponse> selectTodos(
            @Param("tenantId") Long tenantId,
            @Param("assigneeId") Long assigneeId,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM workflow_task",
            "WHERE tenant_id = #{tenantId} AND assignee_user_id = #{assigneeId}",
            "AND business_type = 'LEAVE_APPLICATION'",
            "<if test='status != null and status != \"\"'> AND status = #{status}</if>",
            "<if test='from != null'> AND created_at &gt;= #{from}</if>",
            "<if test='to != null'> AND created_at &lt;= #{to}</if>",
            "</script>"
    })
    long countTodos(
            @Param("tenantId") Long tenantId,
            @Param("assigneeId") Long assigneeId,
            @Param("status") String status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
