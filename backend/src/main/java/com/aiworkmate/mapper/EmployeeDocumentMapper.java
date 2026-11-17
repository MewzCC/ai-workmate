package com.aiworkmate.mapper;

import com.aiworkmate.entity.EmployeeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.aiworkmate.dto.BusinessDocumentMetadata;
import java.util.List;

@Mapper
public interface EmployeeDocumentMapper extends BaseMapper<EmployeeDocument> {
    @Select("""
            SELECT id::text AS "documentId", document_type AS "documentType", display_name AS "displayName",
                   mime_type AS "mimeType", file_size AS "fileSize", created_at AS "createdAt"
            FROM employee_document WHERE tenant_id=#{tenantId} AND employee_user_id=#{employeeId}
            ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}
            """)
    List<BusinessDocumentMetadata> metadataPage(@Param("tenantId") Long tenantId, @Param("employeeId") Long employeeId,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("SELECT count(*) FROM employee_document WHERE tenant_id=#{tenantId} AND employee_user_id=#{employeeId}")
    long metadataCount(@Param("tenantId") Long tenantId, @Param("employeeId") Long employeeId);
}
