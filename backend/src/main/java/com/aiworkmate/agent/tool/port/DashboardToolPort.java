package com.aiworkmate.agent.tool.port;

import java.util.List;

/** Transport-neutral dashboard preferences contract for local or remote domain adapters. */
public interface DashboardToolPort {
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
