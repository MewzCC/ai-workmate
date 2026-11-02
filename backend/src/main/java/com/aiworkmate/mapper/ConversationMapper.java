package com.aiworkmate.mapper;

import com.aiworkmate.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM conversation WHERE id=#{id} AND tenant_id=#{tenantId} AND user_id=#{userId} FOR UPDATE")
    Conversation lockOwned(@org.apache.ibatis.annotations.Param("tenantId") Long tenantId,
                           @org.apache.ibatis.annotations.Param("userId") Long userId,
                           @org.apache.ibatis.annotations.Param("id") Long id);
}
