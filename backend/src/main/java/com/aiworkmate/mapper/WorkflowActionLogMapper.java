package com.aiworkmate.mapper;

import com.aiworkmate.dto.WorkflowTimelineResponse;
import com.aiworkmate.entity.WorkflowActionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WorkflowActionLogMapper extends BaseMapper<WorkflowActionLog> {

    List<WorkflowTimelineResponse> selectTimeline(
            @Param("tenantId") Long tenantId,
            @Param("instanceId") Long instanceId);

    /** 查询同一业务的所有流程尝试，供撤回或拒绝后的重新提交保留完整历史。 */
    List<WorkflowTimelineResponse> selectBusinessTimeline(
            @Param("tenantId") Long tenantId,
            @Param("businessType") String businessType,
            @Param("businessId") Long businessId);

    long countTargetAction(
            @Param("tenantId") Long tenantId,
            @Param("instanceId") Long instanceId,
            @Param("targetUserId") Long targetUserId,
            @Param("action") String action);
}
