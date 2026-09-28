package com.aiworkmate.service.impl;

import com.aiworkmate.common.BusinessException;
import com.aiworkmate.common.ErrorCode;
import com.aiworkmate.dto.DashboardExportResponse;
import com.aiworkmate.dto.ObservabilityChartPreference;
import com.aiworkmate.dto.ObservabilityExportRequest;
import com.aiworkmate.dto.PlatformObservabilityResponse;
import com.aiworkmate.service.BusinessAuditService;
import com.aiworkmate.service.ObservabilityExportService;
import com.aiworkmate.service.ObservabilityPreferenceService;
import com.aiworkmate.service.PlatformObservabilityService;
import com.aiworkmate.service.UserAccessService;
import com.aiworkmate.service.model.ResolvedUserAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObservabilityExportServiceImpl implements ObservabilityExportService {
    private static final int MAX_ROWS = 3_000;
    private final UserAccessService accessService;
    private final PlatformObservabilityService observabilityService;
    private final ObservabilityPreferenceService preferenceService;
    private final BusinessAuditService auditService;

    @Override
    public DashboardExportResponse export(Long userId, ObservabilityExportRequest request) {
        ResolvedUserAccess actor = accessService.resolveActiveUser(userId);
        if (actor == null) throw new BusinessException(ErrorCode.AUTH_REQUIRED);
        if (!actor.permissions().containsAll(Set.of("route:platform-observability", "runtime-log:read", "data:export"))) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED);
        }
        if (request == null || !Set.of("24h", "7d", "30d").contains(request.range())
                || request.chartId() == null || !request.chartId().matches("[a-z0-9-]{1,64}")) {
            throw new BusinessException(ErrorCode.REQUEST_INVALID);
        }
        ObservabilityChartPreference chart = preferenceService.preferences(userId).charts().stream()
                .filter(item -> item.id().equals(request.chartId())).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_INVALID));
        PlatformObservabilityResponse overview = observabilityService.overview(userId, request.range());
        List<String[]> rows = switch (kind(chart)) {
            case "volume", "risk" -> timelineRows(userId, chart, overview);
            case "source" -> overview.sources().stream().filter(item -> chart.content().contains(item.code()))
                    .map(item -> new String[] {item.code(), "", Long.toString(item.total())}).toList();
            case "error" -> overview.errorCodes().stream()
                    .filter(item -> chart.content().isEmpty() || chart.content().contains(item.code()))
                    .map(item -> new String[] {item.code(), "", Long.toString(item.total())}).toList();
            default -> throw new BusinessException(ErrorCode.REQUEST_INVALID);
        };
        if (rows.size() > MAX_ROWS) throw new BusinessException(ErrorCode.REQUEST_INVALID);
        StringBuilder csv = new StringBuilder("\uFEFFseries,bucket,value\r\n");
        rows.forEach(row -> csv.append(csv(row[0])).append(',').append(csv(row[1])).append(',')
                .append(csv(row[2])).append("\r\n"));
        auditService.record(actor.tenantId(), actor.userId(), "PLATFORM_OBSERVABILITY", chart.id(),
                "EXPORT", "SUCCESS", "range=" + request.range() + ",kind=" + kind(chart) + ",rows=" + rows.size());
        return new DashboardExportResponse("observability-" + chart.id() + "-" + request.range() + ".csv",
                "text/csv;charset=UTF-8", csv.toString(), rows.size(), OffsetDateTime.now());
    }

    private List<String[]> timelineRows(Long userId, ObservabilityChartPreference chart,
                                        PlatformObservabilityResponse overview) {
        String interval = chart.granularity() == null || "auto".equals(chart.granularity())
                ? overview.interval() : chart.granularity();
        List<PlatformObservabilityResponse.TimelinePoint> timeline = interval.equals(overview.interval())
                ? overview.timeline() : observabilityService.timeline(userId, overview.from(), overview.to(), interval).timeline();
        Map<LocalDateTime, List<PlatformObservabilityResponse.TimelinePoint>> byBucket = timeline.stream()
                .collect(Collectors.groupingBy(PlatformObservabilityResponse.TimelinePoint::bucket));
        LocalDateTime cursor = "hour".equals(interval) ? overview.from().truncatedTo(ChronoUnit.HOURS)
                : overview.from().truncatedTo(ChronoUnit.DAYS);
        LocalDateTime last = "hour".equals(interval) ? overview.to().truncatedTo(ChronoUnit.HOURS)
                : overview.to().truncatedTo(ChronoUnit.DAYS);
        List<String[]> rows = new ArrayList<>();
        while (!cursor.isAfter(last)) {
            List<PlatformObservabilityResponse.TimelinePoint> points = byBucket.getOrDefault(cursor, List.of());
            for (String series : chart.content()) {
                long value;
                if ("volume".equals(kind(chart))) {
                    value = points.stream().filter(point -> series.equals(point.source()))
                            .mapToLong(point -> point.total() == null ? 0 : point.total()).sum();
                } else {
                    value = points.stream().mapToLong(point -> "failed".equals(series)
                            ? point.failed() == null ? 0 : point.failed()
                            : point.blocked() == null ? 0 : point.blocked()).sum();
                }
                rows.add(new String[] {series, cursor.toString(), Long.toString(value)});
                if (rows.size() > MAX_ROWS) throw new BusinessException(ErrorCode.REQUEST_INVALID);
            }
            cursor = "hour".equals(interval) ? cursor.plusHours(1) : cursor.plusDays(1);
        }
        return rows;
    }

    private String kind(ObservabilityChartPreference chart) {
        return chart.kind() == null ? chart.id() : chart.kind();
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@\t\r\n".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
