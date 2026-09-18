package com.aiworkmate.mapper;

import com.aiworkmate.entity.VisitorBooking;
import com.aiworkmate.entity.VisitorVisitOperation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 访客预约 Mapper。
 *
 * <p>除 {@link BaseMapper} 标准 CRUD 外，提供按 business_type 解析 workflow_definition id
 * 的查询，避免 Service 层硬编码 SQL。
 */
@Mapper
public interface VisitorBookingMapper extends BaseMapper<VisitorBooking> {

    @Select("""
            SELECT wd.id FROM workflow_definition wd
            WHERE wd.tenant_id = #{tenantId}
              AND wd.business_type = 'VISITOR_BOOKING'
              AND wd.enabled = TRUE
            ORDER BY wd.version DESC
            LIMIT 1
            """)
    Long selectDefinitionId(@Param("tenantId") Long tenantId);

    @Select("SELECT * FROM visitor_booking WHERE tenant_id=#{tenantId} "
            + "AND applicant_user_id=#{userId} AND agent_operation_key=#{operationKey} LIMIT 1")
    VisitorBooking findAgentOperation(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                      @Param("operationKey") String operationKey);

    @Select("SELECT * FROM visitor_visit_operation WHERE tenant_id=#{tenantId} "
            + "AND operator_user_id=#{userId} AND agent_operation_key=#{operationKey} LIMIT 1")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    VisitorVisitOperation findVisitAgentOperation(
            @Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("operationKey") String operationKey);

    @Insert("""
            INSERT INTO visitor_visit_operation(
                tenant_id,booking_id,operator_user_id,operation_type,agent_operation_key,
                source_version,result_version,result_status,remark,occurred_at,created_at)
            VALUES(#{tenantId},#{bookingId},#{operatorUserId},#{operationType},#{agentOperationKey},
                #{sourceVersion},#{resultVersion},#{resultStatus},#{remark},#{occurredAt},#{createdAt})
            ON CONFLICT (tenant_id,operator_user_id,agent_operation_key) DO NOTHING
            """)
    int insertVisitAgentOperation(VisitorVisitOperation operation);
}
