package com.aiworkmate.mapper;

import com.aiworkmate.entity.KnowledgeBase;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    @Select("""
            SELECT COUNT(*)
            FROM knowledge_doc
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND kb_id = #{kbId}
            """)
    long countDocuments(@Param("tenantId") Long tenantId,
                        @Param("userId") Long userId,
                        @Param("kbId") Long kbId);

    @Select("""
            SELECT COUNT(*)
            FROM knowledge_chunk kc
            JOIN knowledge_doc kd ON kd.id = kc.doc_id
            WHERE kc.tenant_id = #{tenantId}
              AND kc.user_id = #{userId}
              AND kd.kb_id = #{kbId}
            """)
    long countChunks(@Param("tenantId") Long tenantId,
                     @Param("userId") Long userId,
                     @Param("kbId") Long kbId);
}
