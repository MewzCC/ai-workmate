package com.aiworkmate.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public record DashboardOverviewResponse(
        OffsetDateTime generatedAt,
        int days,
        List<String> availableMetricCodes,
        List<Metric> metrics,
        List<TodoSummary> todos,
        List<TrendPoint> trends,
        List<DistributionItem> businessDistribution,
        List<Activity> recentActivities,
        HealthSummary healthSummary
) {
    public record Metric(String code, long value) {}

    public record TodoSummary(Long taskId, String businessType, Long businessId, String title,
                              String applicantName, LocalDateTime submittedAt, LocalDateTime dueAt,
                              boolean overdue) {}

    public record TrendPoint(LocalDate date, long submitted, long completed) {}

    public record DistributionItem(String businessType, long value) {}

    public record Activity(Long id, String resourceType, String resourceId, String action,
                           String result, String summary, LocalDateTime createdAt) {}

    public record HealthSummary(String status, LocalDateTime checkedAt) {}
}
