package com.aiworkmate.mapper;

import com.aiworkmate.entity.TenantConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TenantConfigurationMapper extends BaseMapper<TenantConfiguration> {
    @Insert("""
            INSERT INTO tenant_configuration(tenant_id, tenant_name, updated_by)
            SELECT id, name, #{userId} FROM tenant WHERE id = #{tenantId}
            ON CONFLICT (tenant_id) DO NOTHING
            """)
    int ensureForTenant(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Update("UPDATE tenant SET name = #{tenantName}, updated_at = CURRENT_TIMESTAMP WHERE id = #{tenantId}")
    int updateTenantName(@Param("tenantId") Long tenantId, @Param("tenantName") String tenantName);
}
