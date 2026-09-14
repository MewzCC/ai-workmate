package com.aiworkmate.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

@Mapper
public interface AiOperationPermissionMapper {

    @Select("""
            SELECT permission_code FROM rbac_role_permission
            WHERE tenant_id = #{tenantId} AND role_code = #{roleCode}
              AND permission_code LIKE 'agent:tool:%'
            ORDER BY permission_code
            """)
    List<String> selectRoleToolPermissions(@Param("tenantId") Long tenantId,
                                           @Param("roleCode") String roleCode);

    @Delete("""
            DELETE FROM rbac_role_permission
            WHERE tenant_id = #{tenantId} AND role_code = #{roleCode}
              AND permission_code LIKE 'agent:tool:%'
            """)
    int deleteRoleToolPermissions(@Param("tenantId") Long tenantId,
                                  @Param("roleCode") String roleCode);

    @Insert({
            "<script>",
            "INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code) VALUES",
            "<foreach collection='permissionCodes' item='code' separator=','>",
            "(#{tenantId}, #{roleCode}, #{code})",
            "</foreach>",
            "ON CONFLICT DO NOTHING",
            "</script>"
    })
    int insertRoleToolPermissions(@Param("tenantId") Long tenantId,
                                  @Param("roleCode") String roleCode,
                                  @Param("permissionCodes") Set<String> permissionCodes);
}
