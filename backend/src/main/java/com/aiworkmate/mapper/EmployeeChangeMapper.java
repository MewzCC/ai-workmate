package com.aiworkmate.mapper;

import com.aiworkmate.dto.EmployeeChangeView;
import com.aiworkmate.entity.EmployeeChange;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDate;

@Mapper
public interface EmployeeChangeMapper extends BaseMapper<EmployeeChange> {
    EmployeeChangeView selectView(@Param("tenantId") Long tenantId, @Param("id") Long id);

    List<EmployeeChangeView> selectPage(@Param("tenantId") Long tenantId,
                                        @Param("status") String status,
                                        @Param("changeType") String changeType,
                                        @Param("keyword") String keyword,
                                        @Param("size") int size,
                                        @Param("offset") int offset);

    long countPage(@Param("tenantId") Long tenantId,
                   @Param("status") String status,
                   @Param("changeType") String changeType,
                   @Param("keyword") String keyword);

    List<EmployeeChange> selectDueForUpdate(@Param("effectiveDate") LocalDate effectiveDate);
}
