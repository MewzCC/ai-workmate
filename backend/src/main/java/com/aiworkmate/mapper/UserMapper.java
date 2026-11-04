package com.aiworkmate.mapper;

import com.aiworkmate.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    // Match UserAccessService's assigned roles, legacy-role fallback and SUPER_ADMIN semantics.
    String BUSINESS_USER_PERMISSION_EXPRESSION = """
                EXISTS (SELECT 1 FROM rbac_role_permission rp
                  WHERE rp.tenant_id = u.tenant_id AND rp.permission_code = #{requiredPermission}
                    AND (EXISTS (SELECT 1 FROM user_role ur WHERE ur.tenant_id = u.tenant_id
                          AND ur.user_id = u.id AND ur.role_code = rp.role_code)
                      OR (NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.tenant_id = u.tenant_id AND ur.user_id = u.id)
                          AND rp.role_code = u.role)))
                OR (EXISTS (SELECT 1 FROM rbac_permission p WHERE p.tenant_id = u.tenant_id AND p.code = #{requiredPermission})
                    AND (EXISTS (SELECT 1 FROM user_role ur WHERE ur.tenant_id = u.tenant_id
                          AND ur.user_id = u.id AND ur.role_code = 'SUPER_ADMIN')
                      OR (NOT EXISTS (SELECT 1 FROM user_role ur WHERE ur.tenant_id = u.tenant_id AND ur.user_id = u.id)
                          AND u.role = 'SUPER_ADMIN')))
            """;

    String BUSINESS_USER_OPTION_FILTER = """
            FROM app_user u
            WHERE u.tenant_id = #{tenantId} AND u.status = 1
            <if test="excludedUserId != null">AND u.id != #{excludedUserId}</if>
            <if test="keyword != null">
              AND (COALESCE(NULLIF(u.display_name, ''), u.username) ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="requiredPermission != null">AND (
            """ + BUSINESS_USER_PERMISSION_EXPRESSION + ")</if>";

    String APPROVAL_PARTICIPANT_FILTER = """
            FROM app_user u
            WHERE u.tenant_id = #{tenantId} AND u.status = 1 AND u.id != #{excludedUserId}
            <if test="keyword != null">
              AND (u.display_name ILIKE CONCAT('%', #{keyword}, '%') OR u.username ILIKE CONCAT('%', #{keyword}, '%'))
            </if>
            <if test="approversOnly">AND u.id != #{applicantId} AND (
            """ + BUSINESS_USER_PERMISSION_EXPRESSION + ")</if>";

    @Select("<script>SELECT u.id, COALESCE(NULLIF(TRIM(u.display_name), ''), u.username) AS name, "
            + "u.avatar, u.updated_at, (u.id != #{applicantId} AND (" + BUSINESS_USER_PERMISSION_EXPRESSION + ")) AS can_approve "
            + APPROVAL_PARTICIPANT_FILTER + " ORDER BY u.id LIMIT #{limit} OFFSET #{offset}</script>")
    java.util.List<com.aiworkmate.dto.ApprovalParticipantRow> selectApprovalParticipants(
            @Param("tenantId") Long tenantId, @Param("requiredPermission") String requiredPermission,
            @Param("excludedUserId") Long excludedUserId, @Param("applicantId") Long applicantId,
            @Param("approversOnly") boolean approversOnly, @Param("keyword") String keyword,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) " + APPROVAL_PARTICIPANT_FILTER + "</script>")
    long countApprovalParticipants(@Param("tenantId") Long tenantId,
            @Param("requiredPermission") String requiredPermission, @Param("excludedUserId") Long excludedUserId,
            @Param("applicantId") Long applicantId, @Param("approversOnly") boolean approversOnly,
            @Param("keyword") String keyword);

    @Select("<script>SELECT u.id, COALESCE(NULLIF(u.display_name, ''), u.username) AS name "
            + BUSINESS_USER_OPTION_FILTER + " ORDER BY u.id LIMIT #{limit} OFFSET #{offset}</script>")
    java.util.List<com.aiworkmate.dto.BusinessUserOption> selectBusinessUserOptions(
            @Param("tenantId") Long tenantId, @Param("requiredPermission") String requiredPermission,
            @Param("excludedUserId") Long excludedUserId, @Param("keyword") String keyword,
            @Param("limit") int limit, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) " + BUSINESS_USER_OPTION_FILTER + "</script>")
    long countBusinessUserOptions(@Param("tenantId") Long tenantId,
            @Param("requiredPermission") String requiredPermission, @Param("excludedUserId") Long excludedUserId,
            @Param("keyword") String keyword);

    @Select("SELECT * FROM app_user WHERE id=#{userId} AND tenant_id=#{tenantId} AND status=1 FOR UPDATE")
    User lockActiveApplicant(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("""
            <script>SELECT u.id FROM app_user u WHERE u.tenant_id=#{tenantId} AND u.status=1 AND u.id != #{applicantId}
            <if test="roleCode != null">AND (EXISTS(SELECT 1 FROM user_role ur WHERE ur.tenant_id=u.tenant_id AND ur.user_id=u.id AND ur.role_code=#{roleCode})
              OR (NOT EXISTS(SELECT 1 FROM user_role ur WHERE ur.tenant_id=u.tenant_id AND ur.user_id=u.id) AND u.role=#{roleCode}))</if>
            <if test="departmentId != null">AND u.department_id=#{departmentId}
              AND EXISTS(SELECT 1 FROM department d WHERE d.id=u.department_id AND d.tenant_id=u.tenant_id AND d.status=1)</if>
            AND (
            """ + BUSINESS_USER_PERMISSION_EXPRESSION + ") ORDER BY u.id LIMIT 51</script>")
    java.util.List<Long> selectWorkflowAssignees(@Param("tenantId") Long tenantId, @Param("applicantId") Long applicantId,
            @Param("requiredPermission") String requiredPermission, @Param("roleCode") String roleCode, @Param("departmentId") Long departmentId);

    @Select("SELECT id FROM tenant WHERE code = 'DEFAULT' AND status = 1")
    Long selectDefaultTenantId();

    @Select("SELECT id FROM department WHERE tenant_id = #{tenantId} AND code = 'HEADQUARTERS' AND status = 1")
    Long selectDefaultDepartmentId(Long tenantId);

    @Select("SELECT id FROM position WHERE tenant_id = #{tenantId} AND code = 'EMPLOYEE' AND status = 1")
    Long selectDefaultPositionId(Long tenantId);

    @Insert("""
            INSERT INTO user_role(tenant_id, user_id, role_code)
            VALUES(#{tenantId}, #{userId}, #{roleCode})
            ON CONFLICT DO NOTHING
            """)
    int insertUserRole(@Param("tenantId") Long tenantId,
                       @Param("userId") Long userId,
                       @Param("roleCode") String roleCode);
}
