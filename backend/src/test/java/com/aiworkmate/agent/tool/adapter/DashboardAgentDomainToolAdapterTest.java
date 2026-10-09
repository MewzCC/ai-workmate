package com.aiworkmate.agent.tool.adapter;

import com.aiworkmate.agent.tool.port.DashboardToolPort;
import com.aiworkmate.agent.tool.port.ToolActorContext;
import com.aiworkmate.dto.DashboardPreferenceRequest;
import com.aiworkmate.dto.DashboardPreferenceResponse;
import com.aiworkmate.service.DashboardService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardAgentDomainToolAdapterTest {
    @Test
    void overviewIncludesOnlyBoundedAggregateFieldsNotSensitivePageRecords() {
        DashboardService service = mock(DashboardService.class);
        var actor = new ToolActorContext(9L, 7L, 20L, 30L, 1, "trace");
        var generatedAt = java.time.OffsetDateTime.parse("2026-10-09T10:00:00+08:00");
        when(service.overview(7L, 30)).thenReturn(new com.aiworkmate.dto.DashboardOverviewResponse(
                generatedAt, 30, List.of("PENDING_TODOS"),
                List.of(new com.aiworkmate.dto.DashboardOverviewResponse.Metric("PENDING_TODOS", 2)),
                List.of(new com.aiworkmate.dto.DashboardOverviewResponse.TodoSummary(
                        99L, "LEAVE_APPLICATION", 100L, "private title", "private name", null, null, false)),
                java.util.stream.IntStream.range(0, 30).mapToObj(i ->
                        new com.aiworkmate.dto.DashboardOverviewResponse.TrendPoint(
                                java.time.LocalDate.of(2026, 10, 9).minusDays(i), i, 0)).toList(),
                java.util.stream.IntStream.range(0, 17).mapToObj(i ->
                        new com.aiworkmate.dto.DashboardOverviewResponse.DistributionItem("TYPE_" + i, i)).toList(),
                List.of(new com.aiworkmate.dto.DashboardOverviewResponse.Activity(
                        1L, "PRIVATE", "2", "UPDATE", "SUCCESS", "private audit", null)), null));

        var result = new DashboardAgentDomainToolAdapter(service).overview(actor,
                new DashboardToolPort.OverviewQuery(30));
        assertThat(result.metrics()).containsExactly(new DashboardToolPort.Metric("PENDING_TODOS", 2));
        assertThat(result.trends()).hasSize(30);
        assertThat(result.businessDistribution()).hasSize(16);
        assertThat(result.distributionTruncated()).isTrue();
        assertThat(result.toString()).doesNotContain("private", "99", "100");
        verify(service).overview(7L, 30);
    }

    @Test
    void delegatesToTheExistingLivePermissionCheckedDomainService() {
        DashboardService service = mock(DashboardService.class);
        var adapter = new DashboardAgentDomainToolAdapter(service);
        var actor = new ToolActorContext(9L, 7L, 20L, 30L, 1, "trace");
        var command = new DashboardToolPort.PreferenceUpdateCommand(
                List.of("MY_APPLICATIONS", "PENDING_TODOS"));
        var request = new DashboardPreferenceRequest(command.metricCodes());
        when(service.updatePreferences(7L, request)).thenReturn(new DashboardPreferenceResponse(
                command.metricCodes(), List.of("PENDING_TODOS", "MY_APPLICATIONS")));

        var result = adapter.updatePreferences(actor, command);

        assertThat(result.metricCodes()).containsExactly("MY_APPLICATIONS", "PENDING_TODOS");
        verify(service).updatePreferences(7L, request);
    }
}
