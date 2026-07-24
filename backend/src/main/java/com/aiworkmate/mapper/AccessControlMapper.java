package com.aiworkmate.mapper;

import com.aiworkmate.dto.AccessPermissionResponse;
import com.aiworkmate.dto.AccessRouteResponse;
import com.aiworkmate.dto.AccessRoleResponse;
import com.aiworkmate.dto.AccessUserResponse;
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

    @Select("""
            SELECT code
            FROM rbac_permission
            ORDER BY module, code
            """)
    List<String> selectAllPermissionCodes();

    @Select("""
            SELECT permission_code
            FROM rbac_role_permission
            WHERE role_code = #{roleCode}
            ORDER BY permission_code
            """)
    List<String> selectPermissionCodes(String roleCode);

    @Select("""
            SELECT u.id,
                   COALESCE(NULLIF(u.display_name, ''), u.username) AS name,
                   u.email,
                   u.role,
                   u.status,
                   u.updated_at AS updatedAt
            FROM app_user u
            ORDER BY u.created_at, u.id
            """)
    List<AccessUserResponse> selectUsers();

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
            SELECT code, name, module, description
            FROM rbac_permission
            ORDER BY module, code
            """)
    List<AccessPermissionResponse> selectPermissions();

    @Select("""
            SELECT route_key AS routeKey, parent_key AS parentKey, name, path, icon,
                   route_type AS routeType, component_key AS componentKey,
                   permission_code AS permissionCode, sort_order AS sortOrder, enabled
            FROM rbac_route
            ORDER BY sort_order, route_key
            """)
    List<AccessRouteResponse> selectRoutes();

    @Select("SELECT COUNT(*) FROM rbac_role WHERE code = #{roleCode}")
    int countRole(String roleCode);

    @Insert("""
            INSERT INTO rbac_role(code, name, description, builtin)
            VALUES (#{code}, #{name}, #{description}, FALSE)
            """)
    int insertRole(@Param("code") String code,
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

    @Insert("""
            INSERT INTO access_audit_log(
                operator_user_id, action, target_type, target_id, before_value, after_value
            ) VALUES (
                #{operatorUserId}, #{action}, #{targetType}, #{targetId}, #{beforeValue}, #{afterValue}
            )
            """)
    int insertAudit(@Param("operatorUserId") Long operatorUserId,
                    @Param("action") String action,
                    @Param("targetType") String targetType,
                    @Param("targetId") String targetId,
                    @Param("beforeValue") String beforeValue,
                    @Param("afterValue") String afterValue);
}
