package com.aiworkmate.mapper;

import com.aiworkmate.entity.SealUsageDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.aiworkmate.dto.BusinessDocumentMetadata;
import java.util.List;

@Mapper
public interface SealUsageDocumentMapper extends BaseMapper<SealUsageDocument> {
    @Select("""
            SELECT id::text AS "documentId", 'ARCHIVE' AS "documentType", display_name AS "displayName",
                   mime_type AS "mimeType", file_size AS "fileSize", created_at AS "createdAt"
            FROM seal_usage_document WHERE tenant_id=#{tenantId} AND seal_usage_id=#{usageId}
            ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<BusinessDocumentMetadata> metadataPage(@Param("tenantId") Long tenantId, @Param("usageId") Long usageId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT count(*) FROM seal_usage_document WHERE tenant_id=#{tenantId} AND seal_usage_id=#{usageId}")
    long metadataCount(@Param("tenantId") Long tenantId, @Param("usageId") Long usageId);
}
