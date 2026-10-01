package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Transport-neutral personal observability preference contract. */
public interface ObservabilityToolPort {
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
