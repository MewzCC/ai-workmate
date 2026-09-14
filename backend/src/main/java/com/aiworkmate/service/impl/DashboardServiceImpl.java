package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.DashboardOverviewResponse;
import com.aiworkmate.dto.DashboardExportRequest;
import com.aiworkmate.dto.DashboardExportResponse;
import com.aiworkmate.dto.DashboardPreferenceRequest;
import com.aiworkmate.dto.DashboardPreferenceResponse;
import com.aiworkmate.mapper.DashboardMapper;
import com.aiworkmate.service.DashboardService;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.UserSettingsService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final String READ_PERMISSION = "dashboard:read";
    private static final List<String> METRIC_CODES = List.of(
            "PENDING_TODOS", "OVERDUE_TODOS", "MY_APPLICATIONS", "UNREAD_MESSAGES");

    private final UserAccessService userAccessService;
    private final DashboardMapper dashboardMapper;
    private final BusinessAuditService auditService;
    private final UserSettingsService userSettingsService;

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

        Map<String, Long> metricValues = new LinkedHashMap<>();
        metricValues.put("PENDING_TODOS", dashboardMapper.countPendingTodos(actor.tenantId(), actor.userId()));
        metricValues.put("OVERDUE_TODOS", dashboardMapper.countOverdueTodos(actor.tenantId(), actor.userId()));
        metricValues.put("MY_APPLICATIONS", dashboardMapper.countMyApplications(actor.tenantId(), actor.userId()));
        metricValues.put("UNREAD_MESSAGES", dashboardMapper.countUnreadMessages(actor.tenantId(), actor.userId()));
        List<DashboardOverviewResponse.Metric> metrics = selectedMetricCodes(actor).stream()
                .map(code -> new DashboardOverviewResponse.Metric(code, metricValues.get(code)))
                .toList();

        return new DashboardOverviewResponse(
                OffsetDateTime.now(), days, METRIC_CODES, metrics,
                dashboardMapper.selectPendingTodos(actor.tenantId(), actor.userId()),
                dashboardMapper.selectTrends(actor.tenantId(), actor.userId(), days),
                dashboardMapper.selectDistribution(actor.tenantId(), actor.userId(), days),
                dashboardMapper.selectRecentActivities(actor.tenantId(), actor.userId(), days),
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardExportResponse export(Long userId, DashboardExportRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, "data:export");
        if (request.from().isAfter(request.to()) || request.from().plusDays(30).isBefore(request.to())) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID, "validation.dashboard.export.range");
        }
        String keyword = request.keyword() == null ? null : request.keyword().trim();
        List<DashboardMapper.DashboardExportRow> rows = dashboardMapper.selectExportRows(
                actor.tenantId(), actor.userId(), request.from().atStartOfDay(),
                request.to().plusDays(1).atStartOfDay(), keyword == null || keyword.isBlank() ? null : keyword);
        String header = "taskId,title,applicant,businessType,submittedAt,dueAt,status,overdue\r\n";
        String content = "\uFEFF" + header + rows.stream().map(this::csvRow).collect(Collectors.joining("\r\n"));
        if (!rows.isEmpty()) content += "\r\n";
        auditService.record(actor.tenantId(), actor.userId(), "DASHBOARD", "overview",
                "EXPORT", "SUCCESS", "rows=" + rows.size() + ",from=" + request.from() + ",to=" + request.to());
        String filename = "dashboard-" + request.from() + "-" + request.to() + ".csv";
        return new DashboardExportResponse(filename, "text/csv;charset=UTF-8", content, rows.size(), OffsetDateTime.now());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardPreferenceResponse preferences(Long userId) {
        ResolvedUserAccess actor = requirePermission(userId, READ_PERMISSION);
        return new DashboardPreferenceResponse(selectedMetricCodes(actor), availableMetricCodes(actor));
    }

    @Override
    @Transactional
    public DashboardPreferenceResponse updatePreferences(Long userId, DashboardPreferenceRequest request) {
        ResolvedUserAccess actor = requirePermission(userId, READ_PERMISSION);
        List<String> available = availableMetricCodes(actor);
        List<String> requested = request.metricCodes();
        if (new LinkedHashSet<>(requested).size() != requested.size()
                || requested.stream().anyMatch(code -> !available.contains(code))) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        userSettingsService.setDashboardMetricCodes(actor.userId(), requested);
        return new DashboardPreferenceResponse(List.copyOf(requested), available);
    }

    private List<String> selectedMetricCodes(ResolvedUserAccess actor) {
        List<String> available = availableMetricCodes(actor);
        List<String> selected = userSettingsService.getDashboardMetricCodes(actor.userId()).stream()
                .filter(available::contains)
                .distinct()
                .toList();
        return selected.isEmpty() ? available : selected;
    }

    private List<String> availableMetricCodes(ResolvedUserAccess actor) {
        return actor.permissions().contains(READ_PERMISSION) ? METRIC_CODES : List.of();
    }

    private ResolvedUserAccess requirePermission(Long userId, String permission) {
        ResolvedUserAccess actor = userAccessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().contains(permission)) throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        return actor;
    }

    private String csvRow(DashboardMapper.DashboardExportRow row) {
        return String.join(",",
                csv(row.taskId()), csv(row.title()), csv(row.applicantName()), csv(row.businessType()),
                csv(format(row.submittedAt())), csv(format(row.dueAt())), csv(row.status()), csv(row.overdue()));
    }

    private String format(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private String csv(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw);
        String trimmed = value.stripLeading();
        if ((!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0)
                || value.startsWith("\t") || value.startsWith("\r")) {
            value = "'" + value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
