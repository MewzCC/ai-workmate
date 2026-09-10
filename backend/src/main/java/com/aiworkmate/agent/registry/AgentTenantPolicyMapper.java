package com.aiworkmate.agent.registry;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AgentTenantPolicyMapper extends BaseMapper<AgentTenantPolicy> {
    @Insert("""
            INSERT INTO agent_tenant_policy(tenant_id, enabled, write_tools_enabled)
            VALUES(#{tenantId}, #{enabled}, #{writeToolsEnabled})
            ON CONFLICT (tenant_id) DO UPDATE SET
                enabled = EXCLUDED.enabled,
                write_tools_enabled = EXCLUDED.write_tools_enabled,
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertSwitches(@Param("tenantId") Long tenantId,
                       @Param("enabled") boolean enabled,
                       @Param("writeToolsEnabled") boolean writeToolsEnabled);
}
