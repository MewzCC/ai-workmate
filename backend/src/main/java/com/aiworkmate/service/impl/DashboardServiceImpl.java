package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.mapper.DashboardMapper;
import com.aiworkmate.service.DashboardService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String READ_PERMISSION = "dashboard:read";
    private static final List<String> METRIC_CODES = List.of(
            "PENDING_TODOS", "OVERDUE_TODOS", "MY_APPLICATIONS", "UNREAD_MESSAGES");

    private final UserAccessService userAccessService;
    private final DashboardMapper dashboardMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardOverviewResponse overview(Long userId, int days) {
        if (days < 1 || days > 30) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.dashboard.days.range");
        }
        ResolvedUserAccess actor = userAccessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains(READ_PERMISSION)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }

        List<DashboardOverviewResponse.Metric> metrics = List.of(
                new DashboardOverviewResponse.Metric("PENDING_TODOS",
                        dashboardMapper.countPendingTodos(actor.tenantId(), actor.userId())),
                new DashboardOverviewResponse.Metric("OVERDUE_TODOS",
                        dashboardMapper.countOverdueTodos(actor.tenantId(), actor.userId())),
                new DashboardOverviewResponse.Metric("MY_APPLICATIONS",
                        dashboardMapper.countMyApplications(actor.tenantId(), actor.userId())),
                new DashboardOverviewResponse.Metric("UNREAD_MESSAGES",
                        dashboardMapper.countUnreadMessages(actor.tenantId(), actor.userId()))
        );

        return new DashboardOverviewResponse(
                OffsetDateTime.now(), days, METRIC_CODES, metrics,
                dashboardMapper.selectPendingTodos(actor.tenantId(), actor.userId()),
                dashboardMapper.selectTrends(actor.tenantId(), actor.userId(), days),
                dashboardMapper.selectDistribution(actor.tenantId(), actor.userId(), days),
                dashboardMapper.selectRecentActivities(actor.tenantId(), actor.userId(), days),
                null
        );
    }
}
