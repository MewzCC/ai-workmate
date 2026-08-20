package com.aiworkmate.mapper;

import com.aiworkmate.entity.SealUsage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 印章用印 Mapper。
 *
 * <p>除 {@link BaseMapper} 标准 CRUD 外，提供按 business_type 解析 workflow_definition id
 * 的查询，避免 Service 层硬编码 SQL。
 */
@Mapper
public interface SealUsageMapper extends BaseMapper<SealUsage> {

    @Select("""
            SELECT wd.id FROM workflow_definition wd
            WHERE wd.tenant_id = #{tenantId}
              AND wd.business_type = 'SEAL_USAGE'
              AND wd.enabled = TRUE
            ORDER BY wd.version DESC
            LIMIT 1
            """)
    Long selectDefinitionId(@Param("tenantId") Long tenantId);
}
