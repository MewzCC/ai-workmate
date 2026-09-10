package com.aiworkmate.mapper;

import com.aiworkmate.entity.BudgetPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface BudgetPlanMapper extends BaseMapper<BudgetPlan> {
    @Select("SELECT COALESCE(SUM(total_amount),0) FROM budget_plan WHERE tenant_id=#{tenantId} AND deleted=FALSE AND status='ACTIVE'")
    BigDecimal totalActive(@Param("tenantId") Long tenantId);
    @Select("SELECT COALESCE(SUM(occupied_amount),0) FROM budget_plan WHERE tenant_id=#{tenantId} AND deleted=FALSE AND status='ACTIVE'")
    BigDecimal totalOccupied(@Param("tenantId") Long tenantId);
    @Select("SELECT COALESCE(SUM(spent_amount),0) FROM budget_plan WHERE tenant_id=#{tenantId} AND deleted=FALSE AND status='ACTIVE'")
    BigDecimal totalSpent(@Param("tenantId") Long tenantId);
}
