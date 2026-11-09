package com.aiworkmate.mapper;

import com.aiworkmate.dto.GenericApprovalTaskItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GenericApprovalTaskMapper {
    String PARTICIPANTS=UserMapper.APPROVAL_PARTICIPANT_FILTER+" AND ("+UserMapper.BUSINESS_USER_PERMISSION_EXPRESSION.replace("#{requiredPermission}","'todo:read'")+") ";
    @SelectProvider(type=ParticipantSql.class,method="rows")
    List<com.aiworkmate.dto.ApprovalParticipantRow> participants(@Param("tenantId") Long tenantId,@Param("requiredPermission") String requiredPermission,
            @Param("excludedUserId") Long excludedUserId,@Param("applicantId") Long applicantId,@Param("approversOnly") boolean approversOnly,
            @Param("keyword") String keyword,@Param("size") int size,@Param("offset") long offset);
    @SelectProvider(type=ParticipantSql.class,method="count")
    long countParticipants(@Param("tenantId") Long tenantId,@Param("requiredPermission") String requiredPermission,
            @Param("excludedUserId") Long excludedUserId,@Param("applicantId") Long applicantId,@Param("approversOnly") boolean approversOnly,@Param("keyword") String keyword);
    /** Fixed code-owned SQL only; user input is always a bound parameter. */
    class ParticipantSql {
        public static String rows(){return "<script>SELECT u.id,COALESCE(NULLIF(TRIM(u.display_name),''),u.username) AS name,u.avatar,u.updated_at,(u.id!=#{applicantId} AND ("+UserMapper.BUSINESS_USER_PERMISSION_EXPRESSION+")) AS can_approve "+PARTICIPANTS+" ORDER BY u.id LIMIT #{size} OFFSET #{offset}</script>";}
        public static String count(){return "<script>SELECT COUNT(*) "+PARTICIPANTS+"</script>";}
    }
    String JOINS = """
            FROM workflow_task wt
            JOIN workflow_instance wi ON wi.id=wt.instance_id AND wi.tenant_id=wt.tenant_id
                AND wi.business_type=wt.business_type AND wi.business_id=wt.business_id
            JOIN approval_application a ON a.id=wt.business_id AND a.tenant_id=wt.tenant_id AND a.applicant_user_id=wi.applicant_id
            JOIN app_user applicant ON applicant.id=wi.applicant_id AND applicant.tenant_id=wt.tenant_id
            LEFT JOIN approval_execution e ON e.instance_id=wi.id AND e.tenant_id=wt.tenant_id AND e.application_id=a.id AND e.applicant_user_id=wi.applicant_id
            WHERE wt.tenant_id=#{tenantId} AND wt.business_type='GENERIC_APPROVAL'
            """;
    String COLUMNS = """
            SELECT wt.id,wt.business_id AS application_id,wi.applicant_id AS applicant_user_id,
                COALESCE(NULLIF(applicant.display_name,''),applicant.username) AS applicant_name,
                COALESCE(e.form_name,'') AS form_name,COALESCE(e.title,'') AS title,
                wt.process_node_name AS node_name,wt.process_node_index AS node_index,wt.status,wt.version,
                COALESCE(e.submitted_at,wi.started_at) AS submitted_at,wt.due_at,
                (wt.status='PENDING' AND wt.due_at IS NOT NULL AND wt.due_at &lt; CURRENT_TIMESTAMP) AS overdue,
                (e.form_schema IS NOT NULL AND e.submitted_at IS NOT NULL) AS has_snapshot
            """;
    String FILTER = """
            AND wt.assignee_user_id=#{userId}
            <if test="status != null">AND wt.status=#{status}</if>
            <if test="from != null">AND wt.created_at &gt;= #{from}</if>
            <if test="to != null">AND wt.created_at &lt;= #{to}</if>
            """;
    @Select("<script>" + COLUMNS + JOINS + FILTER + " ORDER BY wt.created_at DESC,wt.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<GenericApprovalTaskItem> mine(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("status") String status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
            @Param("size") int size, @Param("offset") long offset);
    @Select("<script>SELECT COUNT(*) " + JOINS + FILTER + "</script>")
    long countMine(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("status") String status,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
    @Select("<script>" + COLUMNS + JOINS + " AND wt.id=#{id}</script>")
    GenericApprovalTaskItem item(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
