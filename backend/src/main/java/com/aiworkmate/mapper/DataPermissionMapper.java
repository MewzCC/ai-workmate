package com.aiworkmate.mapper;

import com.aiworkmate.dto.DataPermissionRoleBindingResponse;
import com.aiworkmate.dto.DataPermissionUserExceptionResponse;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Set;

@Mapper
public interface DataPermissionMapper {
    @Select("""
        SELECT p.id, p.name, p.description, p.scope_type AS scopeType, p.enabled,
               p.version, p.updated_at AS updatedAt
        FROM data_permission_policy p WHERE p.tenant_id=#{tenantId}
        ORDER BY p.enabled DESC, p.name, p.id
        """)
    List<PolicyRow> selectPolicies(Long tenantId);

    @Select("SELECT department_id FROM data_permission_policy_department WHERE tenant_id=#{tenantId} AND policy_id=#{policyId} ORDER BY department_id")
    List<Long> selectPolicyDepartments(@Param("tenantId") Long tenantId, @Param("policyId") Long policyId);

    @Select("SELECT role_code AS roleCode, policy_id AS policyId FROM data_permission_role_binding WHERE tenant_id=#{tenantId} ORDER BY role_code")
    List<DataPermissionRoleBindingResponse> selectRoleBindings(Long tenantId);

    @Select("SELECT user_id AS userId, policy_id AS policyId FROM data_permission_user_exception WHERE tenant_id=#{tenantId} ORDER BY user_id")
    List<DataPermissionUserExceptionResponse> selectUserExceptions(Long tenantId);

    @Select("SELECT COUNT(*) FROM data_permission_policy WHERE tenant_id=#{tenantId} AND id=#{id}")
    int countPolicy(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM data_permission_policy WHERE tenant_id=#{tenantId} AND id=#{id} AND enabled=TRUE")
    int countEnabledPolicy(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Insert("""
        INSERT INTO data_permission_policy(tenant_id,name,description,scope_type,enabled,created_by)
        VALUES(#{tenantId},#{name},#{description},#{scopeType},#{enabled},#{actorId})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertPolicy(DataPermissionPolicyWrite row);

    @Update("""
        UPDATE data_permission_policy SET name=#{name},description=#{description},scope_type=#{scopeType},
          enabled=#{enabled},version=version+1,updated_at=CURRENT_TIMESTAMP
        WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version}
        """)
    int updatePolicy(DataPermissionPolicyWrite row);

    @Delete("DELETE FROM data_permission_policy WHERE tenant_id=#{tenantId} AND id=#{id} AND version=#{version}")
    int deletePolicy(@Param("tenantId") Long tenantId, @Param("id") Long id, @Param("version") long version);

    @Delete("DELETE FROM data_permission_policy_department WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}")
    int deletePolicyDepartments(@Param("tenantId") Long tenantId, @Param("policyId") Long policyId);

    @Insert({"<script>", "INSERT INTO data_permission_policy_department(tenant_id,policy_id,department_id) VALUES",
        "<foreach collection='ids' item='id' separator=','>(#{tenantId},#{policyId},#{id})</foreach>", "</script>"})
    int insertPolicyDepartments(@Param("tenantId") Long tenantId, @Param("policyId") Long policyId, @Param("ids") Set<Long> ids);

    @Insert("""
        INSERT INTO data_permission_role_binding(tenant_id,role_code,policy_id,updated_by)
        VALUES(#{tenantId},#{roleCode},#{policyId},#{actorId})
        ON CONFLICT(tenant_id,role_code) DO UPDATE SET policy_id=EXCLUDED.policy_id,
          updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP
        """)
    int bindRole(@Param("tenantId") Long tenantId, @Param("roleCode") String roleCode,
                 @Param("policyId") Long policyId, @Param("actorId") Long actorId);

    @Insert("""
        INSERT INTO data_permission_user_exception(tenant_id,user_id,policy_id,updated_by)
        VALUES(#{tenantId},#{userId},#{policyId},#{actorId})
        ON CONFLICT(tenant_id,user_id) DO UPDATE SET policy_id=EXCLUDED.policy_id,
          updated_by=EXCLUDED.updated_by,updated_at=CURRENT_TIMESTAMP
        """)
    int bindUser(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                 @Param("policyId") Long policyId, @Param("actorId") Long actorId);

    @Delete("DELETE FROM data_permission_user_exception WHERE tenant_id=#{tenantId} AND user_id=#{userId}")
    int deleteUserException(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Update("UPDATE app_user SET permission_version=permission_version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=#{tenantId} AND id=#{userId}")
    int incrementUserPermissionVersion(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("SELECT department_id FROM app_user WHERE tenant_id=#{tenantId} AND id=#{userId} AND status=1")
    Long selectActiveUserDepartment(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM data_permission_role_binding WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}")
    int countRoleBindings(@Param("tenantId") Long tenantId, @Param("policyId") Long policyId);
    @Select("SELECT COUNT(*) FROM data_permission_user_exception WHERE tenant_id=#{tenantId} AND policy_id=#{policyId}")
    int countUserBindings(@Param("tenantId") Long tenantId, @Param("policyId") Long policyId);

    @Select("""
        SELECT DISTINCT p.scope_type FROM data_permission_policy p
        LEFT JOIN data_permission_role_binding rb ON rb.tenant_id=p.tenant_id AND rb.policy_id=p.id
        LEFT JOIN user_role ur ON ur.tenant_id=rb.tenant_id AND ur.role_code=rb.role_code AND ur.user_id=#{userId}
        LEFT JOIN data_permission_user_exception ue ON ue.tenant_id=p.tenant_id AND ue.policy_id=p.id AND ue.user_id=#{userId}
        WHERE p.tenant_id=#{tenantId} AND p.enabled=TRUE
          AND ((EXISTS(SELECT 1 FROM data_permission_user_exception x WHERE x.tenant_id=#{tenantId} AND x.user_id=#{userId}) AND ue.user_id IS NOT NULL)
            OR (NOT EXISTS(SELECT 1 FROM data_permission_user_exception x WHERE x.tenant_id=#{tenantId} AND x.user_id=#{userId}) AND ur.user_id IS NOT NULL))
        ORDER BY p.scope_type
        """)
    List<String> selectEffectiveScopes(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Select("""
        SELECT DISTINCT pd.department_id FROM data_permission_policy_department pd
        JOIN data_permission_policy p ON p.tenant_id=pd.tenant_id AND p.id=pd.policy_id AND p.enabled=TRUE
        LEFT JOIN data_permission_role_binding rb ON rb.tenant_id=p.tenant_id AND rb.policy_id=p.id
        LEFT JOIN user_role ur ON ur.tenant_id=rb.tenant_id AND ur.role_code=rb.role_code AND ur.user_id=#{userId}
        LEFT JOIN data_permission_user_exception ue ON ue.tenant_id=p.tenant_id AND ue.policy_id=p.id AND ue.user_id=#{userId}
        WHERE p.tenant_id=#{tenantId} AND ((EXISTS(SELECT 1 FROM data_permission_user_exception x WHERE x.tenant_id=#{tenantId} AND x.user_id=#{userId}) AND ue.user_id IS NOT NULL)
          OR (NOT EXISTS(SELECT 1 FROM data_permission_user_exception x WHERE x.tenant_id=#{tenantId} AND x.user_id=#{userId}) AND ur.user_id IS NOT NULL))
        ORDER BY pd.department_id
        """)
    List<Long> selectEffectiveCustomDepartments(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    record PolicyRow(Long id, String name, String description, String scopeType,
                     boolean enabled, long version, java.time.LocalDateTime updatedAt) {}

    @lombok.Data
    class DataPermissionPolicyWrite {
        private Long id; private Long tenantId; private Long actorId; private String name; private String description;
        private String scopeType; private boolean enabled; private long version;
    }
}
