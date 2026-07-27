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
}
