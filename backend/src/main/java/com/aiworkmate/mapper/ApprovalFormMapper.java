package com.aiworkmate.mapper;

import com.aiworkmate.entity.ApprovalForm;
import com.aiworkmate.dto.ApprovalFormReferenceOption;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 审批表单定义 Mapper。
 *
 * 表单引用候选只返回当前租户启用记录的编号与名称。
 */
@Mapper
public interface ApprovalFormMapper extends BaseMapper<ApprovalForm> {
    String REFERENCE_FILTER = """
            FROM (
                <choose>
                    <when test="person">SELECT id, COALESCE(NULLIF(display_name, ''), username) AS name
                        FROM app_user WHERE tenant_id = #{tenantId} AND status = 1</when>
                    <otherwise>SELECT id, name FROM department WHERE tenant_id = #{tenantId} AND status = 1</otherwise>
                </choose>
            ) AS candidate
            <where>
                <if test="id != null">AND id = #{id}</if>
                <if test="keyword != null">AND STRPOS(LOWER(name), LOWER(#{keyword})) > 0</if>
            </where>
            """;

    @Select("<script>SELECT CAST(id AS text) AS id, name " + REFERENCE_FILTER
            + " ORDER BY id LIMIT #{limit} OFFSET #{offset}</script>")
    List<ApprovalFormReferenceOption> referenceOptions(@Param("tenantId") long tenantId,
            @Param("person") boolean person, @Param("id") Long id, @Param("keyword") String keyword,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) " + REFERENCE_FILTER + "</script>")
    long countReferenceOptions(@Param("tenantId") long tenantId, @Param("person") boolean person,
            @Param("id") Long id, @Param("keyword") String keyword);
}
