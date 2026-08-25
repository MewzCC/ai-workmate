package com.aiworkmate.agent.registry;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
