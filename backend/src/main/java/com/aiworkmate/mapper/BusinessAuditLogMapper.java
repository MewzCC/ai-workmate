package com.aiworkmate.mapper;

import com.aiworkmate.dto.AuditRecordResponse;
import com.aiworkmate.entity.BusinessAuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessAuditLogMapper extends BaseMapper<BusinessAuditLog> {

    @Select({
            "<script>",
            "SELECT audit.id, audit.actor_user_id AS actorUserId,",
            "COALESCE(NULLIF(u.display_name, ''), u.username) AS actorName,",
            "audit.resource_type AS resourceType, audit.resource_id AS resourceId,",
            "audit.action, audit.result, audit.summary, audit.trace_id AS traceId,",
            "audit.created_at AS createdAt",
            "FROM business_audit_log audit",
            "JOIN app_user u ON u.id = audit.actor_user_id",
            "WHERE audit.tenant_id = #{tenantId}",
            "<if test='actorUserId != null'> AND audit.actor_user_id = #{actorUserId}</if>",
            "<if test='action != null and action != \"\"'> AND audit.action = #{action}</if>",
            "<if test='resourceType != null and resourceType != \"\"'> AND audit.resource_type = #{resourceType}</if>",
            "<if test='result != null and result != \"\"'> AND audit.result = #{result}</if>",
            "<if test='from != null'> AND audit.created_at &gt;= #{from}</if>",
            "<if test='to != null'> AND audit.created_at &lt;= #{to}</if>",
            "ORDER BY audit.created_at DESC, audit.id DESC",
            "LIMIT #{size} OFFSET #{offset}",
            "</script>"
    })
    List<AuditRecordResponse> selectRecords(
            @Param("tenantId") Long tenantId,
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("result") String result,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("size") int size,
            @Param("offset") int offset);

    @Select({
            "<script>",
            "SELECT COUNT(*) FROM business_audit_log",
            "WHERE tenant_id = #{tenantId}",
            "<if test='actorUserId != null'> AND actor_user_id = #{actorUserId}</if>",
            "<if test='action != null and action != \"\"'> AND action = #{action}</if>",
            "<if test='resourceType != null and resourceType != \"\"'> AND resource_type = #{resourceType}</if>",
            "<if test='result != null and result != \"\"'> AND result = #{result}</if>",
            "<if test='from != null'> AND created_at &gt;= #{from}</if>",
            "<if test='to != null'> AND created_at &lt;= #{to}</if>",
            "</script>"
    })
    long countRecords(
            @Param("tenantId") Long tenantId,
            @Param("actorUserId") Long actorUserId,
            @Param("action") String action,
            @Param("resourceType") String resourceType,
            @Param("result") String result,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
