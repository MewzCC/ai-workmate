package com.aiworkmate.agent.registry;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

@Mapper
public interface AgentToolMapper extends BaseMapper<AgentTool> {

    @Select("""
            SELECT * FROM agent_tool
            WHERE tenant_id IS NULL AND code = #{code}
            """)
    AgentTool selectPlatformTool(String code);

    @Select("""
            SELECT * FROM agent_tool
            WHERE tenant_id = #{tenantId} AND code = #{code}
            """)
    AgentTool selectTenantTool(@Param("tenantId") Long tenantId, @Param("code") String code);

    @Select("SELECT * FROM agent_tool WHERE tenant_id IS NULL ORDER BY code")
    List<AgentTool> selectPlatformTools();

    @Select("SELECT * FROM agent_tool WHERE tenant_id = #{tenantId} ORDER BY code")
    List<AgentTool> selectTenantTools(Long tenantId);

    @Insert("""
            INSERT INTO agent_tool(
                tenant_id, code, name, description, handler_version, parameters_schema,
                output_schema, schema_hash, risk_level, required_permissions, permission_mode,
                data_scope_policy, retry_policy, side_effect, confirmation_policy,
                max_result_items, max_result_bytes, timeout_ms, audit_level, enabled
            )
            SELECT #{tenantId}, code, name, description, handler_version, parameters_schema,
                   output_schema, schema_hash, risk_level, required_permissions, permission_mode,
                   data_scope_policy, retry_policy, side_effect, confirmation_policy,
                   max_result_items, max_result_bytes, timeout_ms, audit_level, #{enabled}
            FROM agent_tool
            WHERE tenant_id IS NULL AND code = #{code}
            ON CONFLICT (tenant_id, code) WHERE tenant_id IS NOT NULL DO UPDATE SET
                enabled = EXCLUDED.enabled,
                updated_at = CURRENT_TIMESTAMP
            """)
    int upsertTenantEnabled(@Param("tenantId") Long tenantId,
                            @Param("code") String code,
                            @Param("enabled") boolean enabled);
}
