package com.aiworkmate.mapper;

import com.aiworkmate.entity.BusinessContract;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface BusinessContractMapper extends BaseMapper<BusinessContract> {
    @Select("""
            SELECT COALESCE(SUM(amount - paid_amount), 0)
            FROM business_contract
            WHERE tenant_id = #{tenantId} AND deleted = FALSE AND status = 'ACTIVE'
            """)
    BigDecimal selectOutstandingAmount(@Param("tenantId") Long tenantId);
}
