package com.aiworkmate.mapper;

import com.aiworkmate.dto.AccessPermissionResponse;
import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessUserResponse;
import com.aiworkmate.dto.AccessUserRow;
import com.aiworkmate.dto.DepartmentResponse;
import com.aiworkmate.dto.PositionResponse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Set;

@Mapper
public interface AccessControlMapper {

    @Select("SELECT id FROM tenant WHERE code = 'DEFAULT'")
    Long selectDefaultTenantId();

    @Select("""
            SELECT code
            FROM rbac_permission
            ORDER BY module, code
            """)
    List<String> selectAllPermissionCodes();

    @Select("""
            SELECT code
            FROM rbac_permission
            WHERE tenant_id = #{tenantId}
            ORDER BY module, code
            """)
    List<String> selectAllPermissionCodesForTenant(Long tenantId);

    @Select("""
            SELECT permission_code
            FROM rbac_role_permission
            WHERE role_code = #{roleCode}
            ORDER BY permission_code
            """)
    List<String> selectPermissionCodes(String roleCode);

    @Select({
            "<script>",
            "SELECT DISTINCT permission_code",
            "FROM rbac_role_permission",
            "WHERE tenant_id = #{tenantId} AND role_code IN",
            "<foreach collection='roleCodes' item='roleCode' open='(' separator=',' close=')'>",
            "#{roleCode}",
            "</foreach>",
            "ORDER BY permission_code",
            "</script>"
    })
    List<String> selectPermissionCodesForRoles(
            @Param("tenantId") Long tenantId,
            @Param("roleCodes") List<String> roleCodes);

    @Select("""
            SELECT role_code
            FROM user_role
            WHERE tenant_id = #{tenantId} AND user_id = #{userId}
            ORDER BY CASE role_code
                WHEN 'SUPER_ADMIN' THEN 1
                WHEN 'SYSTEM_ADMIN' THEN 2
                WHEN 'PROCESS_ADMIN' THEN 3
                WHEN 'FINANCE_ADMIN' THEN 4
                ELSE 5 END, role_code
            """)
    List<String> selectUserRoleCodes(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId);

    @Select({
            "<script>",
            "SELECT DISTINCT scope_type",
            "FROM data_scope",
            "WHERE tenant_id = #{tenantId} AND role_code IN",
            "<foreach collection='roleCodes' item='roleCode' open='(' separator=',' close=')'>",
            "#{roleCode}",
            "</foreach>",
            "ORDER BY scope_type",
            "</script>"
    })
    List<String> selectDataScopes(
            @Param("tenantId") Long tenantId,
            @Param("roleCodes") List<String> roleCodes);

    @Select("""
            SELECT u.id,
                   COALESCE(NULLIF(u.display_name, ''), u.username) AS name,
                   u.email,
                   u.role,
                   u.status,
                   u.tenant_id AS tenantId,
                   u.department_id AS departmentId,
                   u.position_id AS positionId,
                   u.approver_user_id AS approverUserId,
                   u.permission_version AS permissionVersion,
                   u.updated_at AS updatedAt
            FROM app_user u
            WHERE u.tenant_id = #{tenantId}
            ORDER BY u.created_at, u.id
            """)
    List<AccessUserRow> selectUsers(Long tenantId);

    @Select("""
            SELECT id, code, name, parent_id AS parentId,
                   default_approver_user_id AS defaultApproverUserId, status
            FROM department
            WHERE tenant_id = #{tenantId}
            ORDER BY name, id
            """)
    List<DepartmentResponse> selectDepartments(Long tenantId);

    @Select("""
            SELECT id, code, name, status
            FROM position
            WHERE tenant_id = #{tenantId}
            ORDER BY name, id
            """)
    List<PositionResponse> selectPositions(Long tenantId);

    @Select("""
            SELECT code, name, description, builtin
            FROM rbac_role
            ORDER BY CASE code
                WHEN 'SUPER_ADMIN' THEN 1
                WHEN 'SYSTEM_ADMIN' THEN 2
                WHEN 'PROCESS_ADMIN' THEN 3
                WHEN 'FINANCE_ADMIN' THEN 4
                ELSE 5 END
            """)
    List<AccessRoleResponse> selectRoles();

    @Select("""
            SELECT code, name, description, builtin
            FROM rbac_role
            WHERE tenant_id = #{tenantId}
            ORDER BY CASE code
                WHEN 'SUPER_ADMIN' THEN 1
                WHEN 'SYSTEM_ADMIN' THEN 2
                WHEN 'PROCESS_ADMIN' THEN 3
                WHEN 'FINANCE_ADMIN' THEN 4
                ELSE 5 END
            """)
    List<AccessRoleResponse> selectRolesForTenant(Long tenantId);

    @Select("""
            SELECT code, name, module, description
            FROM rbac_permission
            ORDER BY module, code
            """)
    List<AccessPermissionResponse> selectPermissions();

    @Select("""
            SELECT code, name, module, description
            FROM rbac_permission
            WHERE tenant_id = #{tenantId}
            ORDER BY module, code
            """)
    List<AccessPermissionResponse> selectPermissionsForTenant(Long tenantId);

    @Select("""
            SELECT route_key AS routeKey, parent_key AS parentKey, name, path, icon,
                   route_type AS routeType, component_key AS componentKey,
                   permission_code AS permissionCode, sort_order AS sortOrder, enabled
            FROM rbac_route
            ORDER BY sort_order, route_key
            """)
    List<AccessRouteResponse> selectRoutes();

    @Select("""
            SELECT route_key AS routeKey, parent_key AS parentKey, name, path, icon,
                   route_type AS routeType, component_key AS componentKey,
                   permission_code AS permissionCode, sort_order AS sortOrder, enabled
            FROM rbac_route
            WHERE tenant_id = #{tenantId}
            ORDER BY sort_order, route_key
            """)
    List<AccessRouteResponse> selectRoutesForTenant(Long tenantId);

    @Select("SELECT COUNT(*) FROM rbac_role WHERE code = #{roleCode}")
    int countRole(String roleCode);

    @Select("SELECT COUNT(*) FROM rbac_role WHERE tenant_id = #{tenantId} AND code = #{roleCode}")
    int countRoleForTenant(@Param("tenantId") Long tenantId, @Param("roleCode") String roleCode);

    @Insert("""
            INSERT INTO rbac_role(code, name, description, builtin)
            VALUES (#{code}, #{name}, #{description}, FALSE)
            """)
    int insertRole(@Param("code") String code,
                   @Param("name") String name,
                   @Param("description") String description);

    @Insert("""
            INSERT INTO rbac_role(code, name, description, builtin, tenant_id)
            VALUES (#{code}, #{name}, #{description}, FALSE, #{tenantId})
            """)
    int insertRoleForTenant(@Param("tenantId") Long tenantId,
                            @Param("code") String code,
                            @Param("name") String name,
                            @Param("description") String description);

    @Select("SELECT COUNT(*) FROM rbac_route WHERE route_key = #{routeKey}")
    int countRoute(String routeKey);

    @Select("SELECT COUNT(*) FROM rbac_route WHERE path = #{path} AND route_key <> #{routeKey}")
    int countOtherRoutePath(@Param("routeKey") String routeKey, @Param("path") String path);

    @Insert("""
            INSERT INTO rbac_permission(code, name, module, description)
            VALUES (#{code}, #{name}, '页面访问', #{description})
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                module = EXCLUDED.module,
                description = EXCLUDED.description
            """)
    int upsertPermission(@Param("code") String code,
                         @Param("name") String name,
                         @Param("description") String description);

    @Insert("""
            INSERT INTO rbac_permission(code, name, module, description, tenant_id)
            VALUES (#{code}, #{name}, '页面访问', #{description}, #{tenantId})
            ON CONFLICT (code) DO UPDATE SET
                name = EXCLUDED.name,
                module = EXCLUDED.module,
                description = EXCLUDED.description,
                tenant_id = EXCLUDED.tenant_id
            """)
    int upsertPermissionForTenant(@Param("tenantId") Long tenantId,
                                  @Param("code") String code,
                                  @Param("name") String name,
                                  @Param("description") String description);

    @Insert("""
            INSERT INTO rbac_route(
                route_key, parent_key, name, path, icon, route_type, component_key,
                permission_code, sort_order, enabled
            ) VALUES (
                #{routeKey}, #{parentKey}, #{name}, #{path}, #{icon}, #{routeType}, #{componentKey},
                #{permissionCode}, #{sortOrder}, #{enabled}
            )
            """)
    int insertRoute(@Param("routeKey") String routeKey,
                    @Param("parentKey") String parentKey,
                    @Param("name") String name,
                    @Param("path") String path,
                    @Param("icon") String icon,
                    @Param("routeType") String routeType,
                    @Param("componentKey") String componentKey,
                    @Param("permissionCode") String permissionCode,
                    @Param("sortOrder") int sortOrder,
                    @Param("enabled") boolean enabled);

    @Insert("""
            INSERT INTO rbac_route(
                route_key, parent_key, name, path, icon, route_type, component_key,
                permission_code, sort_order, enabled, tenant_id
            ) VALUES (
                #{routeKey}, #{parentKey}, #{name}, #{path}, #{icon}, #{routeType}, #{componentKey},
                #{permissionCode}, #{sortOrder}, #{enabled}, #{tenantId}
            )
            """)
    int insertRouteForTenant(@Param("tenantId") Long tenantId,
                             @Param("routeKey") String routeKey,
                             @Param("parentKey") String parentKey,
                             @Param("name") String name,
                             @Param("path") String path,
                             @Param("icon") String icon,
                             @Param("routeType") String routeType,
                             @Param("componentKey") String componentKey,
                             @Param("permissionCode") String permissionCode,
                             @Param("sortOrder") int sortOrder,
                             @Param("enabled") boolean enabled);

    @Update("""
            UPDATE rbac_route
            SET parent_key = #{parentKey}, name = #{name}, path = #{path}, icon = #{icon},
                route_type = #{routeType}, component_key = #{componentKey},
                permission_code = #{permissionCode}, sort_order = #{sortOrder},
                enabled = #{enabled}, updated_at = CURRENT_TIMESTAMP
            WHERE route_key = #{routeKey}
            """)
    int updateRoute(@Param("routeKey") String routeKey,
                    @Param("parentKey") String parentKey,
                    @Param("name") String name,
                    @Param("path") String path,
                    @Param("icon") String icon,
                    @Param("routeType") String routeType,
                    @Param("componentKey") String componentKey,
                    @Param("permissionCode") String permissionCode,
                    @Param("sortOrder") int sortOrder,
                    @Param("enabled") boolean enabled);

    @Select("""
            SELECT COUNT(*)
            FROM app_user
            WHERE role = 'SUPER_ADMIN' AND status = 1
            """)
    int countActiveSuperAdmins();

    @Select("SELECT role FROM app_user WHERE id = #{userId}")
    String selectUserRole(Long userId);

    @Update("""
            UPDATE app_user
            SET role = #{roleCode}, updated_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
            """)
    int updateUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    @Select("SELECT tenant_id FROM app_user WHERE id = #{userId}")
    Long selectUserTenantId(Long userId);

    @Delete("DELETE FROM user_role WHERE tenant_id = #{tenantId} AND user_id = #{userId}")
    int deleteUserRoles(@Param("tenantId") Long tenantId, @Param("userId") Long userId);

    @Insert({
            "<script>",
            "INSERT INTO user_role(tenant_id, user_id, role_code) VALUES",
            "<foreach collection='roleCodes' item='roleCode' separator=','>",
            "(#{tenantId}, #{userId}, #{roleCode})",
            "</foreach>",
            "</script>"
    })
    int insertUserRoles(@Param("tenantId") Long tenantId,
                        @Param("userId") Long userId,
                        @Param("roleCodes") Set<String> roleCodes);

    @Update("""
            UPDATE app_user
            SET role = #{primaryRole},
                permission_version = permission_version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{userId}
            """)
    int updateUserRolesVersion(@Param("tenantId") Long tenantId,
                               @Param("userId") Long userId,
                               @Param("primaryRole") String primaryRole);

    @Update("""
            UPDATE app_user
            SET permission_version = permission_version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId}
              AND id IN (
                SELECT user_id FROM user_role
                WHERE tenant_id = #{tenantId} AND role_code = #{roleCode}
              )
            """)
    int incrementPermissionVersionForRole(@Param("tenantId") Long tenantId,
                                          @Param("roleCode") String roleCode);

    @Select("""
            SELECT COUNT(DISTINCT u.id)
            FROM app_user u
            JOIN user_role ur ON ur.tenant_id = u.tenant_id AND ur.user_id = u.id
            WHERE u.tenant_id = #{tenantId}
              AND u.status = 1
              AND ur.role_code = 'SUPER_ADMIN'
            """)
    int countActiveSuperAdminsForTenant(Long tenantId);

    @Update("""
            UPDATE app_user
            SET department_id = #{departmentId},
                position_id = #{positionId},
                approver_user_id = #{approverUserId},
                updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{userId}
            """)
    int updateUserOrganization(@Param("tenantId") Long tenantId,
                               @Param("userId") Long userId,
                               @Param("departmentId") Long departmentId,
                               @Param("positionId") Long positionId,
                               @Param("approverUserId") Long approverUserId);

    @Update("""
            UPDATE app_user
            SET status = #{status}, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId} AND id = #{userId}
            """)
    int updateUserStatus(@Param("tenantId") Long tenantId,
                         @Param("userId") Long userId,
                         @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM department WHERE tenant_id = #{tenantId} AND id = #{id}")
    int countDepartment(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM position WHERE tenant_id = #{tenantId} AND id = #{id}")
    int countPosition(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("""
            SELECT COUNT(*) FROM app_user
            WHERE tenant_id = #{tenantId} AND id = #{id} AND status = 1
            """)
    int countActiveUser(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Select("""
            SELECT COUNT(*) FROM app_user
            WHERE tenant_id = #{tenantId} AND department_id = #{departmentId}
            """)
    int countUsersInDepartment(@Param("tenantId") Long tenantId, @Param("departmentId") Long departmentId);

    @Select("""
            SELECT COUNT(*) FROM app_user
            WHERE tenant_id = #{tenantId} AND position_id = #{positionId}
            """)
    int countUsersInPosition(@Param("tenantId") Long tenantId, @Param("positionId") Long positionId);

    @Select("""
            SELECT COUNT(*) FROM department
            WHERE tenant_id = #{tenantId} AND parent_id = #{parentId}
            """)
    int countChildDepartments(@Param("tenantId") Long tenantId, @Param("parentId") Long parentId);

    @Delete("DELETE FROM department WHERE tenant_id = #{tenantId} AND id = #{id}")
    int deleteDepartment(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Delete("DELETE FROM position WHERE tenant_id = #{tenantId} AND id = #{id}")
    int deletePosition(@Param("tenantId") Long tenantId, @Param("id") Long id);

    @Insert("""
            INSERT INTO department(tenant_id, code, name, parent_id, default_approver_user_id)
            VALUES(#{tenantId}, #{code}, #{name}, #{parentId}, #{defaultApproverUserId})
            ON CONFLICT (tenant_id, code) DO UPDATE SET
                name = EXCLUDED.name,
                parent_id = EXCLUDED.parent_id,
                default_approver_user_id = EXCLUDED.default_approver_user_id,
                updated_at = CURRENT_TIMESTAMP
            """)
    int saveDepartment(@Param("tenantId") Long tenantId,
                       @Param("code") String code,
                       @Param("name") String name,
                       @Param("parentId") Long parentId,
                       @Param("defaultApproverUserId") Long defaultApproverUserId);

    @Insert("""
            INSERT INTO position(tenant_id, code, name)
            VALUES(#{tenantId}, #{code}, #{name})
            ON CONFLICT (tenant_id, code) DO UPDATE SET
                name = EXCLUDED.name,
                updated_at = CURRENT_TIMESTAMP
            """)
    int savePosition(@Param("tenantId") Long tenantId,
                     @Param("code") String code,
                     @Param("name") String name);

    @Delete("DELETE FROM rbac_role_permission WHERE role_code = #{roleCode}")
    int deleteRolePermissions(String roleCode);

    @Insert({
            "<script>",
            "INSERT INTO rbac_role_permission(role_code, permission_code) VALUES",
            "<foreach collection='permissionCodes' item='code' separator=','>",
            "(#{roleCode}, #{code})",
            "</foreach>",
            "</script>"
    })
    int insertRolePermissions(@Param("roleCode") String roleCode,
                              @Param("permissionCodes") Set<String> permissionCodes);

    @Delete("""
            DELETE FROM rbac_role_permission
            WHERE tenant_id = #{tenantId} AND role_code = #{roleCode}
            """)
    int deleteRolePermissionsForTenant(@Param("tenantId") Long tenantId,
                                       @Param("roleCode") String roleCode);

    @Insert({
            "<script>",
            "INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code) VALUES",
            "<foreach collection='permissionCodes' item='code' separator=','>",
            "(#{tenantId}, #{roleCode}, #{code})",
            "</foreach>",
            "</script>"
    })
    int insertRolePermissionsForTenant(@Param("tenantId") Long tenantId,
                                       @Param("roleCode") String roleCode,
                                       @Param("permissionCodes") Set<String> permissionCodes);

    @Insert("""
            INSERT INTO access_audit_log(
                operator_user_id, tenant_id, action, target_type, target_id,
                before_value, after_value, result
            ) VALUES (
                #{operatorUserId},
                (SELECT tenant_id FROM app_user WHERE id = #{operatorUserId}),
                #{action}, #{targetType}, #{targetId}, #{beforeValue}, #{afterValue}, 'SUCCESS'
            )
            """)
    int insertAudit(@Param("operatorUserId") Long operatorUserId,
                    @Param("action") String action,
                    @Param("targetType") String targetType,
                    @Param("targetId") String targetId,
                    @Param("beforeValue") String beforeValue,
                    @Param("afterValue") String afterValue);
}
