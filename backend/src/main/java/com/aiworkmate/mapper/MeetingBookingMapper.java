package com.aiworkmate.mapper;

import com.aiworkmate.entity.MeetingBooking;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MeetingBookingMapper extends BaseMapper<MeetingBooking> {
    @Select("SELECT * FROM meeting_booking WHERE tenant_id=#{tenantId} "
            + "AND organizer_user_id=#{userId} AND agent_operation_key=#{operationKey} LIMIT 1")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    MeetingBooking findAgentOperation(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                      @Param("operationKey") String operationKey);
}
