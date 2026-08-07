package com.aiworkmate.mapper;

import com.aiworkmate.entity.AttendanceRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤打卡记录 Mapper。
 *
 * <p>统计与多条件查询在 Service 层用 {@code LambdaQueryWrapper} 完成，
 * 此处仅保留 {@link BaseMapper} 提供的标准 CRUD。
 */
@Mapper
public interface AttendanceRecordMapper extends BaseMapper<AttendanceRecord> {
}
