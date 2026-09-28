package com.aiworkmate.mapper;

import com.aiworkmate.dto.PlatformObservabilityResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "OBSERVABILITY_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class RuntimeLogMapperPostgresIntegrationTest {
    @Autowired RuntimeLogMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("OBSERVABILITY_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("OBSERVABILITY_TEST_DB_USERNAME", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("OBSERVABILITY_TEST_DB_PASSWORD", "postgres"));
    }

    @Test
    @Transactional
    void completeDashboardAggregatesAgainstRealPostgres() {
        Long userId = jdbc.queryForObject("SELECT id FROM app_user ORDER BY id LIMIT 1", Long.class);
        Long tenantId = jdbc.queryForObject("SELECT tenant_id FROM app_user WHERE id = ?", Long.class, userId);
        LocalDateTime from = LocalDateTime.of(2099, 1, 2, 10, 0);
        LocalDateTime to = from.plusHours(1);
        String marker = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO platform_operation_log(tenant_id, user_id, actor_label, event_type,
                   outcome, duration_ms, request_id, trace_id, error_code, started_at)
                VALUES (?, ?, 'observability-test', 'HTTP_READ', 'FAILED', 120, ?, ?, 'TEST_FAILURE', ?)
                """, tenantId, userId, marker, marker, from.plusMinutes(10));

        var stats = mapper.selectStats(tenantId, null, null, null, null, null, from, to, false);
        assertThat(stats.total()).isEqualTo(1L);
        assertThat(stats.failed()).isEqualTo(1L);
        assertThat(mapper.selectP95Duration(tenantId, from, to)).isEqualTo(120L);
        for (String interval : List.of("hour", "day")) {
            List<PlatformObservabilityResponse.TimelinePoint> timeline = mapper.selectTimeline(tenantId, from, to, interval);
            assertThat(timeline).hasSize(1);
            assertThat(timeline.get(0).source()).isEqualTo("HUMAN");
            assertThat(timeline.get(0).total()).isEqualTo(1L);
            assertThat(timeline.get(0).failed()).isEqualTo(1L);
        }
        assertThat(mapper.selectSourceCounts(tenantId, from, to)).containsExactly(
                new PlatformObservabilityResponse.CategoryCount("HUMAN", 1L));
        assertThat(mapper.selectTopErrorCodes(tenantId, from, to)).containsExactly(
                new PlatformObservabilityResponse.CategoryCount("TEST_FAILURE", 1L));
    }
}
