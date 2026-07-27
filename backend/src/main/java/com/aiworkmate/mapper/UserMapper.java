package com.aiworkmate.mapper;

import com.aiworkmate.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper extends BaseMapper<User> {

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
