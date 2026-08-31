package com.aiworkmate.mapper;

import com.aiworkmate.entity.MeetingRoom;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会议室 Mapper。
 *
 * <p>多条件查询在 Service 层用 {@code LambdaQueryWrapper} 完成，
 * 此处仅保留 {@link BaseMapper} 提供的标准 CRUD。
 */
@Mapper
public interface MeetingRoomMapper extends BaseMapper<MeetingRoom> {
    @Select("SELECT * FROM meeting_room "
            + "WHERE id = #{id} AND tenant_id = #{tenantId} AND deleted = FALSE FOR UPDATE")
    MeetingRoom lockForBooking(@Param("tenantId") Long tenantId, @Param("id") Long id);
}
