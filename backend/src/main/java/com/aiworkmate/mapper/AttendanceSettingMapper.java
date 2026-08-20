package com.aiworkmate.mapper;

import com.aiworkmate.entity.AttendanceSetting;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 考勤上下班时间配置数据访问。
 */
@Mapper
public interface AttendanceSettingMapper extends BaseMapper<AttendanceSetting> {
}
