package com.aiworkmate.mapper;

import com.aiworkmate.dto.ApprovalApplicationView;
import com.aiworkmate.entity.ApprovalApplication;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ApprovalApplicationMapper extends BaseMapper<ApprovalApplication> {

    ApprovalApplicationView selectView(
            @Param("tenantId") Long tenantId,
            @Param("id") Long id);

    List<ApprovalApplicationView> selectMine(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("size") int size,
            @Param("offset") int offset);

    long countMine(
            @Param("tenantId") Long tenantId,
            @Param("userId") Long userId,
            @Param("status") String status);

    /** 按角色解析审批人：同租户在职用户中取最小 id，排除申请人。 */
    Long resolveRoleApprover(
            @Param("tenantId") Long tenantId,
            @Param("roleCode") String roleCode,
            @Param("excludeUserId") Long excludeUserId);

    Long selectGenericDefinitionId(@Param("tenantId") Long tenantId);
}
