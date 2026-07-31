package com.aiworkmate.mapper;

import com.aiworkmate.entity.KnowledgeDocument;
import com.aiworkmate.service.model.KnowledgeSearchRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    @Insert("""
            INSERT INTO knowledge_chunk(
                tenant_id, user_id, doc_id, chunk_index, content, embedding,
                embedding_provider, embedding_model, metadata
            ) VALUES (
                #{tenantId}, #{userId}, #{docId}, #{chunkIndex}, #{content},
                CAST(#{embedding} AS vector), #{embeddingProvider}, #{embeddingModel},
                CAST(#{metadata} AS jsonb)
            )
            """)
    int insertChunk(@Param("tenantId") Long tenantId,
                    @Param("userId") Long userId,
                    @Param("docId") Long docId,
                    @Param("chunkIndex") int chunkIndex,
                    @Param("content") String content,
                    @Param("embedding") String embedding,
                    @Param("embeddingProvider") String embeddingProvider,
                    @Param("embeddingModel") String embeddingModel,
                    @Param("metadata") String metadata);

    @Delete("""
            DELETE FROM knowledge_chunk
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND doc_id = #{docId}
            """)
    int deleteChunks(@Param("tenantId") Long tenantId,
                     @Param("userId") Long userId,
                     @Param("docId") Long docId);

    @Select("""
            SELECT content
            FROM knowledge_chunk
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND doc_id = #{docId}
            ORDER BY chunk_index
            """)
    List<String> selectChunkContents(@Param("tenantId") Long tenantId,
                                     @Param("userId") Long userId,
                                     @Param("docId") Long docId);

    @Update("""
            UPDATE knowledge_chunk
            SET embedding = CAST(#{embedding} AS vector),
                embedding_provider = #{embeddingProvider},
                embedding_model = #{embeddingModel}
            WHERE tenant_id = #{tenantId}
              AND user_id = #{userId}
              AND doc_id = #{docId}
              AND chunk_index = #{chunkIndex}
            """)
    int updateChunkEmbedding(@Param("tenantId") Long tenantId,
                             @Param("userId") Long userId,
                             @Param("docId") Long docId,
                             @Param("chunkIndex") int chunkIndex,
                             @Param("embedding") String embedding,
                             @Param("embeddingProvider") String embeddingProvider,
                             @Param("embeddingModel") String embeddingModel);

    @Select("""
            SELECT kc.id AS chunk_id,
                   kd.id AS doc_id,
                   kd.filename,
                   kc.chunk_index,
                   kc.content,
                   1 - (kc.embedding <=> CAST(#{embedding} AS vector)) AS score
            FROM knowledge_chunk kc
            JOIN knowledge_doc kd
              ON kd.id = kc.doc_id
             AND kd.tenant_id = kc.tenant_id
             AND kd.user_id = kc.user_id
            WHERE kc.tenant_id = #{tenantId}
              AND kc.user_id = #{userId}
              AND kd.status = 'READY'
              AND kc.embedding_provider = #{embeddingProvider}
              AND kc.embedding_model = #{embeddingModel}
              AND 1 - (kc.embedding <=> CAST(#{embedding} AS vector)) >= #{minScore}
            ORDER BY kc.embedding <=> CAST(#{embedding} AS vector), kc.id
            LIMIT #{topK}
            """)
    List<KnowledgeSearchRow> search(@Param("tenantId") Long tenantId,
                                    @Param("userId") Long userId,
                                    @Param("embedding") String embedding,
                                    @Param("embeddingProvider") String embeddingProvider,
                                    @Param("embeddingModel") String embeddingModel,
                                    @Param("minScore") double minScore,
                                    @Param("topK") int topK);
}
