package com.aiworkmate.agent.registry;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AgentPageActionPolicyMapper extends BaseMapper<AgentPageActionPolicy> {
    @Select("SELECT * FROM agent_page_action_policy WHERE tenant_id = #{tenantId} ORDER BY page_id, tool_code")
    List<AgentPageActionPolicy> selectByTenant(Long tenantId);

    @Select("SELECT * FROM agent_page_action_policy WHERE tenant_id = #{tenantId} AND page_id = #{pageId} ORDER BY tool_code")
    List<AgentPageActionPolicy> selectByTenantAndPage(@Param("tenantId") Long tenantId,
                                                       @Param("pageId") String pageId);

    @Select("SELECT * FROM agent_page_action_policy WHERE tenant_id = #{tenantId} AND page_id = #{pageId} AND tool_code = #{toolCode}")
    AgentPageActionPolicy selectExact(@Param("tenantId") Long tenantId,
                                      @Param("pageId") String pageId,
                                      @Param("toolCode") String toolCode);

    @Update("""
            UPDATE agent_page_action_policy
            SET enabled = #{enabled}, version = version + 1, updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND page_id = #{pageId} AND tool_code = #{toolCode} AND version = #{version}
            """)
    int updateEnabled(@Param("tenantId") Long tenantId,
                      @Param("pageId") String pageId,
                      @Param("toolCode") String toolCode,
                      @Param("enabled") boolean enabled,
                      @Param("updatedBy") Long updatedBy,
                      @Param("version") Integer version);
}
