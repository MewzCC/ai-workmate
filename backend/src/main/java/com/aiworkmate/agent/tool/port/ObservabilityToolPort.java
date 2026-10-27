package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Transport-neutral observability aggregates and personal preferences contract. */
public interface ObservabilityToolPort {
    OverviewResult overview(ToolActorContext context, OverviewQuery query);

    record OverviewQuery(String range) { }
    record Stats(long total, long succeeded, long failed, long blocked, Long averageDurationMs) { }
    record Timeline(String bucket, long total, long failed, long blocked) { }
    record Category(String code, long total) { }
    record OverviewResult(String from, String to, String interval, Stats stats, Long p95DurationMs,
                          List<Timeline> timeline, List<Category> sources, List<Category> errorCodes,
                          boolean categoriesTruncated) {
        public OverviewResult {
            timeline = List.copyOf(timeline);
            sources = List.copyOf(sources);
            errorCodes = List.copyOf(errorCodes);
        }
    }

    ChartPreferenceResult updateChartPreferences(ToolActorContext context, ChartPreferenceCommand command);

    ThresholdPreferenceResult updateThresholds(ToolActorContext context, ThresholdPreferenceCommand command);

    record ChartPreference(String id, String kind, String title, String mode,
                           List<String> content, String size, String granularity) {
        public ChartPreference { content = List.copyOf(content); }
    }

    record ChartPreferenceCommand(List<ChartPreference> charts) {
        public ChartPreferenceCommand { charts = List.copyOf(charts); }
    }

    record ChartPreferenceResult(List<ChartPreference> charts) implements ToolWriteReceipt {
        public ChartPreferenceResult { charts = List.copyOf(charts); }
    }

    record ThresholdPreferenceCommand(Integer failedCount, Integer blockedCount, Integer p95DurationMs) { }

    record ThresholdPreferenceResult(Integer failedCount, Integer blockedCount, Integer p95DurationMs)
            implements ToolWriteReceipt { }
}
