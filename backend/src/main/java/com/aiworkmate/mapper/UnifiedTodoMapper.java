package com.aiworkmate.mapper;

import com.aiworkmate.dto.UnifiedTodoResponse;
import com.aiworkmate.service.model.TodoReadScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UnifiedTodoMapper {
    String TASK_TITLE = """
            CASE WHEN wt.business_type='LEAVE_APPLICATION'
                THEN CONCAT('LV-',LPAD(wt.business_id::text,GREATEST(6,LENGTH(wt.business_id::text)),'0'))
                WHEN wt.business_type='GENERIC_APPROVAL' THEN COALESCE(e.title,CONCAT('GA-',LPAD(wt.business_id::text,GREATEST(6,LENGTH(wt.business_id::text)),'0')))
                WHEN wt.business_type='VISITOR_BOOKING' THEN LEFT(CONCAT('VB-',wt.business_id,' · ',v.visitor_name),200)
                WHEN wt.business_type='SEAL_USAGE' THEN LEFT(CONCAT('SU-',wt.business_id,' · ',s.document_title),200) END
            """;
    String RELATIONS = """
            FROM workflow_task wt
            JOIN workflow_instance wi ON wi.id=wt.instance_id AND wi.tenant_id=wt.tenant_id
                AND wi.business_type=wt.business_type AND wi.business_id=wt.business_id
            JOIN app_user applicant ON applicant.id=wi.applicant_id AND applicant.tenant_id=wt.tenant_id
            LEFT JOIN leave_application l ON wt.business_type='LEAVE_APPLICATION' AND l.id=wt.business_id
                AND l.tenant_id=wt.tenant_id AND l.applicant_user_id=wi.applicant_id
            LEFT JOIN approval_application a ON wt.business_type='GENERIC_APPROVAL' AND a.id=wt.business_id
                AND a.tenant_id=wt.tenant_id AND a.applicant_user_id=wi.applicant_id
            LEFT JOIN approval_execution e ON e.instance_id=wi.id AND e.tenant_id=wt.tenant_id
                AND e.application_id=a.id AND e.applicant_user_id=wi.applicant_id AND e.submitted_at IS NOT NULL AND e.form_schema IS NOT NULL
            LEFT JOIN visitor_booking v ON wt.business_type='VISITOR_BOOKING' AND v.id=wt.business_id
                AND v.tenant_id=wt.tenant_id AND v.applicant_user_id=wi.applicant_id
                AND v.workflow_instance_id=wi.id AND v.approver_user_id=wt.assignee_user_id
            LEFT JOIN seal_usage s ON wt.business_type='SEAL_USAGE' AND s.id=wt.business_id
                AND s.tenant_id=wt.tenant_id AND s.applicant_user_id=wi.applicant_id
                AND s.workflow_instance_id=wi.id AND s.approver_user_id=wt.assignee_user_id
            WHERE wt.tenant_id=#{tenantId} AND wt.assignee_user_id=#{userId}
                AND wt.status IN ('PENDING','APPROVED','REJECTED','CANCELLED')
                AND (wt.status!='PENDING' OR wi.status='RUNNING')
                AND ((wt.business_type='LEAVE_APPLICATION' AND l.id IS NOT NULL)
                    OR (wt.business_type='GENERIC_APPROVAL' AND a.id IS NOT NULL)
                    OR (wt.business_type='VISITOR_BOOKING' AND v.id IS NOT NULL AND #{scope.visitorReadable} AND (
                        (wt.status='PENDING' AND v.status='PENDING' AND wi.status='RUNNING')
                        OR (wt.status='APPROVED' AND v.status IN ('APPROVED','CHECKED_IN','VISITED','LEFT','NO_SHOW') AND wi.status='COMPLETED')
                        OR (wt.status='REJECTED' AND v.status='REJECTED' AND wi.status='COMPLETED')
                        OR (wt.status='CANCELLED' AND v.status='WITHDRAWN' AND wi.status='CANCELLED')))
                    OR (wt.business_type='SEAL_USAGE' AND s.id IS NOT NULL AND #{scope.sealReadable} AND (
                        (wt.status='PENDING' AND s.status='PENDING' AND wi.status='RUNNING')
                        OR (wt.status='APPROVED' AND s.status IN ('APPROVED','USED','RETURNED') AND wi.status='COMPLETED')
                        OR (wt.status='REJECTED' AND s.status='REJECTED' AND wi.status='COMPLETED')
                        OR (wt.status='CANCELLED' AND s.status='WITHDRAWN' AND wi.status='CANCELLED'))))
            """;
    String DETAIL_AVAILABLE="(wt.business_type IN ('LEAVE_APPLICATION','VISITOR_BOOKING','SEAL_USAGE') OR e.instance_id IS NOT NULL)";
    String FILTERS = """
            <if test="businessType != 'ALL'">AND wt.business_type=#{businessType}</if>
            <if test="status != 'ALL'">AND wt.status=#{status}</if>
            <if test="from != null">AND wt.created_at &gt;= #{from}</if>
            <if test="to != null">AND wt.created_at &lt;= #{to}</if>
            """;
    String COLUMNS = """
            SELECT wt.id,wt.business_id AS application_id,wi.applicant_id AS applicant_user_id,
                COALESCE(NULLIF(applicant.display_name,''),applicant.username) AS applicant_name,
                CASE WHEN l.workflow_instance_id=wi.id AND l.status!='DRAFT' AND l.submitted_at IS NOT NULL THEN l.leave_type END AS leave_type,
                CASE WHEN l.workflow_instance_id=wi.id AND l.status!='DRAFT' AND l.submitted_at IS NOT NULL THEN l.duration_half_days END AS duration_half_days,
                wt.status,wt.version,wi.started_at AS submitted_at,wt.due_at,
                (wt.status='PENDING' AND wt.due_at IS NOT NULL AND wt.due_at &lt; CURRENT_TIMESTAMP) AS overdue,
                applicant.avatar AS applicant_avatar,applicant.updated_at AS applicant_updated_at,NULL AS applicant_avatar_url,
                wt.business_type,
            """ + TASK_TITLE + """
                AS title,
                wt.process_node_name AS node_name,
            """+DETAIL_AVAILABLE+" AS detail_available ";
    @Select("<script>" + COLUMNS + RELATIONS + FILTERS + " ORDER BY CASE WHEN wt.status='PENDING' THEN 0 ELSE 1 END,wt.created_at DESC,wt.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<UnifiedTodoResponse> mine(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("scope") TodoReadScope scope,
            @Param("businessType") String businessType,@Param("status") String status,@Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,@Param("size") int size,@Param("offset") long offset);
    @Select("<script>SELECT COUNT(*) " + RELATIONS + FILTERS + "</script>")
    long count(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("scope") TodoReadScope scope,@Param("businessType") String businessType,
            @Param("status") String status,@Param("from") LocalDateTime from,@Param("to") LocalDateTime to);
    @Select("<script>" + COLUMNS + RELATIONS + " AND wt.id=#{id}</script>")
    UnifiedTodoResponse summary(@Param("tenantId") Long tenantId,@Param("userId") Long userId,@Param("scope") TodoReadScope scope,@Param("id") Long id);
}
