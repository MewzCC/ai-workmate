package com.aiworkmate.mapper;

import com.aiworkmate.entity.VisitorBooking;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
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
}
