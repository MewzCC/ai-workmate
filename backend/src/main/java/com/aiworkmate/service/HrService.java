package com.aiworkmate.service;

import com.aiworkmate.dto.EmployeeDetailResponse;
import com.aiworkmate.dto.OrganizationOverviewResponse;

public interface HrService {

    OrganizationOverviewResponse overview(Long tenantId);

    /**
     * 查询指定租户下某员工的档案详情。
     *
     * @param tenantId   当前认证租户
     * @param employeeId 目标员工 id
     * @return 员工综合档案（基本信息/任职/考勤概览/近期记录）
     * @throws com.aiworkmate.common.BusinessException 员工不存在或不属于当前租户时抛出
     */
    EmployeeDetailResponse employeeDetail(Long tenantId, Long employeeId);
}
