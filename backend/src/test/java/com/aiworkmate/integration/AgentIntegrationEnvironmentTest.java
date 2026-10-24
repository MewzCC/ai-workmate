package com.aiworkmate.integration;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AgentIntegrationEnvironmentTest {
    @Test void permitsOnlyPlainLocalDatabaseUrls() {
        assertThat(AgentIntegrationEnvironment.requireLocalDatabaseUrl("jdbc:postgresql://localhost:5432/test_db"))
                .isEqualTo("jdbc:postgresql://localhost:5432/test_db");
        assertThat(AgentIntegrationEnvironment.requireLocalDatabaseUrl("jdbc:postgresql://127.0.0.1/test_db"))
                .isEqualTo("jdbc:postgresql://127.0.0.1/test_db");
        for (String url : new String[]{null, "", "jdbc:postgresql://remote.invalid/test_db",
                "jdbc:postgresql://localhost/", "jdbc:postgresql://user:secret@localhost/test_db",
                "jdbc:postgresql://localhost/test_db?currentSchema=public", "jdbc:h2:mem:test"}) {
            assertThatThrownBy(() -> AgentIntegrationEnvironment.requireLocalDatabaseUrl(url)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void allocatesUniqueSchemasAndNeverTreatsExistingNamespacesAsOwned() {
        var first = new AgentIntegrationEnvironment(); var second = new AgentIntegrationEnvironment();
        assertThat(first.schema()).matches("agent_it_[a-f0-9]{32}").isNotEqualTo(second.schema());
        for (String schema : new String[]{"public", "agent_it_", "agent_it_../../public", "agent_it_" + "0".repeat(31)}) {
            assertThat(AgentIntegrationEnvironment.isOwnedSchemaName(schema)).isFalse();
        }
        assertThat(AgentIntegrationEnvironment.isOwnedSchemaName(first.schema())).isTrue();
        first.close(); first.close(); second.close();
    }
    @Test void aPongIsNotEnoughToIdentifyTheOwnedRedisProcess() {
        assertThat(AgentIntegrationEnvironment.belongsToRedisProcess("# Server\r\nprocess_id:123\r\n", 123)).isTrue();
        for (String info : new String[]{"", "process_id:321\r\n", "process_id:123\r\nprocess_id:321\r\n"}) {
            assertThat(AgentIntegrationEnvironment.belongsToRedisProcess(info, 123)).isFalse();
        }
    }
    @Test void refusesUnexpectedDatabaseBindingBeforeAnyRedisCheck() {
        var environment = new AgentIntegrationEnvironment();
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var redis = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT current_database()", String.class)).thenReturn("existing_db");
        assertThatThrownBy(() -> environment.verifyBindings(jdbc, redis)).isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verifyNoInteractions(redis);
        environment.close();
    }
    @Test void refusesPublicSchemaEvenInsideTheOwnedDatabase() {
        var environment = new AgentIntegrationEnvironment();
        var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var redis = org.mockito.Mockito.mock(org.springframework.data.redis.core.StringRedisTemplate.class);
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT current_database()", String.class)).thenReturn(environment.schema());
        org.mockito.Mockito.when(jdbc.queryForObject("SELECT current_schema()", String.class)).thenReturn("public");
        assertThatThrownBy(() -> environment.verifyBindings(jdbc, redis)).isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verifyNoInteractions(redis);
        environment.close();
    }
}
