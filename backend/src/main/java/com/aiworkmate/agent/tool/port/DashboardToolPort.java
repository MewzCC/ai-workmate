package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Transport-neutral personal dashboard contract for local or remote domain adapters. */
public interface DashboardToolPort {
    OverviewResult overview(ToolActorContext context, OverviewQuery query);

    record OverviewQuery(int days) { }

    record Metric(String code, long value) { }
    record Trend(String date, long submitted, long completed) { }
    record Distribution(String businessType, long value) { }
    record OverviewResult(String generatedAt, int days, List<Metric> metrics, List<Trend> trends,
                          List<Distribution> businessDistribution, boolean distributionTruncated) {
        public OverviewResult {
            metrics = List.copyOf(metrics);
            trends = List.copyOf(trends);
            businessDistribution = List.copyOf(businessDistribution);
        }
    }

    PreferenceUpdateResult updatePreferences(
            ToolActorContext context, PreferenceUpdateCommand command);

    record PreferenceUpdateCommand(List<String> metricCodes) {
        public PreferenceUpdateCommand { metricCodes = List.copyOf(metricCodes); }
    }

    record PreferenceUpdateResult(List<String> metricCodes, List<String> availableMetricCodes)
            implements ToolWriteReceipt {
        public PreferenceUpdateResult {
            metricCodes = List.copyOf(metricCodes);
            availableMetricCodes = List.copyOf(availableMetricCodes);
        }
    }
}
