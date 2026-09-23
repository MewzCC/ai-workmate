package com.aiworkmate.mapper;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLogMapperSqlTest {
    @Test
    void chartDrilldownUsesTheSameExactGroupsAndExclusiveBoundaryForRowsAndTotals() {
        Configuration configuration = new Configuration();
        configuration.addMapper(RuntimeLogMapper.class);
        Map<String, Object> filters = new HashMap<>();
        filters.put("tenantId", 9L);
        filters.put("source", "AGENT");
        filters.put("outcome", null);
        filters.put("group", "FAILED");
        filters.put("errorCode", "TIMEOUT");
        filters.put("keyword", null);
        filters.put("from", LocalDateTime.of(2026, 9, 23, 10, 0));
        filters.put("to", LocalDateTime.of(2026, 9, 23, 11, 0));
        filters.put("toExclusive", true);
        filters.put("size", 20);
        filters.put("offset", 0);

        for (String method : new String[] {"selectPage", "selectStats"}) {
            MappedStatement statement = configuration.getMappedStatement(RuntimeLogMapper.class.getName() + "." + method);
            String sql = statement.getBoundSql(filters).getSql();
            assertThat(sql).contains("tenant_id = ?", "outcome IN ('FAILED', 'TIMED_OUT', 'RESULT_INVALID')",
                    "error_code = ?", "started_at < ?");
            assertThat(sql).doesNotContain("started_at <= ?");
        }

        filters.put("group", "BLOCKED");
        filters.put("toExclusive", false);
        String sql = configuration.getMappedStatement(RuntimeLogMapper.class.getName() + ".selectStats")
                .getBoundSql(filters).getSql();
        assertThat(sql).contains("outcome = 'REJECTED' OR decision IN", "started_at <= ?");
    }
}
