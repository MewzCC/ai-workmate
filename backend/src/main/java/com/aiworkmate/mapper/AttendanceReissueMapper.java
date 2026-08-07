package com.aiworkmate.mapper;

import com.aiworkmate.entity.AttendanceReissue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 补卡申请 Mapper。
 *
 * <p>多条件查询在 Service 层用 {@code LambdaQueryWrapper} 完成，
 * 此处仅保留 {@link BaseMapper} 提供的标准 CRUD。
 */
@Mapper
public interface AttendanceReissueMapper extends BaseMapper<AttendanceReissue> {
}
