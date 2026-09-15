package com.aiworkmate.mapper;

import com.aiworkmate.entity.AttendanceReissue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 补卡申请 Mapper。
 *
 * <p>多条件查询在 Service 层用 {@code LambdaQueryWrapper} 完成，
 * 此处仅保留 {@link BaseMapper} 提供的标准 CRUD。
 */
@Mapper
public interface AttendanceReissueMapper extends BaseMapper<AttendanceReissue> {
    @Select("SELECT * FROM attendance_reissue WHERE tenant_id=#{tenantId} "
            + "AND applicant_user_id=#{userId} AND agent_operation_key=#{operationKey} LIMIT 1")
    AttendanceReissue findAgentOperation(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                         @Param("operationKey") String operationKey);
}
