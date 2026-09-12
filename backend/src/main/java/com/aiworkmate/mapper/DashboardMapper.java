package com.aiworkmate.mapper;

import com.aiworkmate.dto.DashboardOverviewResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DashboardMapper {

    @Select("SELECT COUNT(*) FROM workflow_task WHERE tenant_id=#{tenantId} AND assignee_user_id=#{userId} AND status='PENDING'")
    long countPendingTodos(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("""
            SELECT COUNT(*) FROM workflow_task
            WHERE tenant_id=#{tenantId} AND assignee_user_id=#{userId} AND status='PENDING'
              AND due_at IS NOT NULL AND due_at < CURRENT_TIMESTAMP
            """)
    long countOverdueTodos(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM leave_application WHERE tenant_id=#{tenantId} AND applicant_user_id=#{userId})
              +
              (SELECT COUNT(*) FROM approval_application WHERE tenant_id=#{tenantId} AND applicant_user_id=#{userId})
            """)
    long countMyApplications(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM notification WHERE tenant_id=#{tenantId} AND user_id=#{userId} AND read_flag=FALSE")
    long countUnreadMessages(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("""
            SELECT wt.id AS taskId, wt.business_type AS businessType, wt.business_id AS businessId,
                   COALESCE(aa.title,
                     CASE WHEN wt.business_type='LEAVE_APPLICATION'
                       THEN CONCAT('LV-', LPAD(wt.business_id::text, 6, '0'))
                       ELSE CONCAT(wt.business_type, '-', wt.business_id) END) AS title,
                   COALESCE(NULLIF(applicant.display_name, ''), applicant.username) AS applicantName,
                   wi.started_at AS submittedAt, wt.due_at AS dueAt,
                   (wt.due_at IS NOT NULL AND wt.due_at < CURRENT_TIMESTAMP) AS overdue
            FROM workflow_task wt
            JOIN workflow_instance wi ON wi.tenant_id=wt.tenant_id AND wi.id=wt.instance_id
            JOIN app_user applicant ON applicant.tenant_id=wt.tenant_id AND applicant.id=wi.applicant_id
            LEFT JOIN approval_application aa ON aa.tenant_id=wt.tenant_id
              AND wt.business_type='GENERIC_APPROVAL' AND aa.id=wt.business_id
            WHERE wt.tenant_id=#{tenantId} AND wt.assignee_user_id=#{userId} AND wt.status='PENDING'
            ORDER BY (wt.due_at IS NULL), wt.due_at, wt.created_at DESC, wt.id DESC
            LIMIT 8
            """)
    List<DashboardOverviewResponse.TodoSummary> selectPendingTodos(
            @Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("""
            WITH days AS (
              SELECT generate_series(CURRENT_DATE - (#{days} - 1) * INTERVAL '1 day',
                CURRENT_DATE, INTERVAL '1 day')::date AS day
            ), submitted AS (
              SELECT wi.started_at::date AS day, COUNT(*) AS value
              FROM workflow_instance wi
              WHERE wi.tenant_id=#{tenantId} AND wi.applicant_id=#{userId}
                AND wi.started_at >= CURRENT_DATE - (#{days} - 1) * INTERVAL '1 day'
              GROUP BY wi.started_at::date
            ), completed AS (
              SELECT wi.completed_at::date AS day, COUNT(*) AS value
              FROM workflow_instance wi
              WHERE wi.tenant_id=#{tenantId} AND wi.applicant_id=#{userId}
                AND wi.completed_at >= CURRENT_DATE - (#{days} - 1) * INTERVAL '1 day'
              GROUP BY wi.completed_at::date
            )
            SELECT days.day AS date, COALESCE(submitted.value, 0) AS submitted,
                   COALESCE(completed.value, 0) AS completed
            FROM days
            LEFT JOIN submitted ON submitted.day=days.day
            LEFT JOIN completed ON completed.day=days.day
            ORDER BY days.day
            """)
    List<DashboardOverviewResponse.TrendPoint> selectTrends(
            @Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("days") int days);

    @Select("""
            SELECT wi.business_type AS businessType, COUNT(*) AS value
            FROM workflow_instance wi
            WHERE wi.tenant_id=#{tenantId} AND wi.applicant_id=#{userId}
              AND wi.started_at >= CURRENT_DATE - (#{days} - 1) * INTERVAL '1 day'
            GROUP BY wi.business_type
            ORDER BY value DESC, wi.business_type
            """)
    List<DashboardOverviewResponse.DistributionItem> selectDistribution(
            @Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("days") int days);

    @Select("""
            SELECT id, resource_type AS resourceType, resource_id AS resourceId,
                   action, result, summary, created_at AS createdAt
            FROM business_audit_log
            WHERE tenant_id=#{tenantId} AND actor_user_id=#{userId}
              AND created_at >= CURRENT_DATE - (#{days} - 1) * INTERVAL '1 day'
            ORDER BY created_at DESC, id DESC
            LIMIT 8
            """)
    List<DashboardOverviewResponse.Activity> selectRecentActivities(
            @Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("days") int days);
}
