package com.aiworkmate.integration;

import com.aiworkmate.AiWorkMateApplication;
import com.aiworkmate.dto.ApprovalDraftRequest;
import com.aiworkmate.dto.VersionRequest;
import com.aiworkmate.service.GenericApprovalService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GenericApprovalExecutionMigrationIntegrationTest extends AbstractIsolatedAgentIntegrationTest {
    private static final AgentIntegrationEnvironment ENVIRONMENT = new AgentIntegrationEnvironment();
    private static final String SECRET = UUID.randomUUID().toString() + UUID.randomUUID();
    @Override protected AgentIntegrationEnvironment environment() { return ENVIRONMENT; }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource datasource;
    @Autowired GenericApprovalService approvals;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        AgentBrowserFixture.registerLocal(p, ENVIRONMENT, AgentBrowserFixture.reserveUiPort(), SECRET);
    }
    @Test void migratesEmptyAndExistingDatabasesThenStartsANewBackendAgainstTheUpgradedOwnedDatabase() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(ENVIRONMENT.schema());
        flyway().validate(); assertThat(flyway().info().pending()).isEmpty();
        long tenant = jdbc.queryForObject("SELECT id FROM tenant WHERE code='DEFAULT'", Long.class);
        var http = new AgentHttpTestFixture(jdbc,json,passwords,port,"en-US");
        var owner = http.createAndLogin(tenant,List.of("route:approval-start","approval:create","approval:submit"));
        LeaveWriteFixture.linkedApprover(jdbc,tenant,owner.id());
        String key = "migration_" + UUID.randomUUID().toString().replace("-", "");
        long form = jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status) VALUES (?,?,'Historic form','{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}','ENABLED') RETURNING id",Long.class,tenant,key);
        jdbc.update("INSERT INTO approval_process(tenant_id,process_key,process_name,form_id,node_json,status) VALUES (?,?,'Review',?,'[{\"nodeName\":\"Review\",\"approveType\":\"DIRECT_MANAGER\"}]','ENABLED')",tenant,key,form);
        long id = approvals.createDraft(owner.id(),new ApprovalDraftRequest(key,key,Map.of("reason","Historic payload"))).id();
        approvals.submitDraft(owner.id(),id,new VersionRequest(0));
        long instance = jdbc.queryForObject("SELECT workflow_instance_id FROM approval_application WHERE id=?",Long.class,id);
        long unproven = jdbc.queryForObject("INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status) SELECT tenant_id,definition_id,business_type,business_id,applicant_id,'COMPLETED' FROM workflow_instance WHERE id=? RETURNING id",Long.class,instance);
        long editedDraft = approvals.createDraft(owner.id(),new ApprovalDraftRequest(key,key,Map.of("reason","Private edited draft"))).id();
        long editedInstance = jdbc.queryForObject("INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status) SELECT tenant_id,definition_id,business_type,?,applicant_id,'COMPLETED' FROM workflow_instance WHERE id=? RETURNING id",Long.class,editedDraft,instance);
        jdbc.update("UPDATE approval_application SET workflow_instance_id=?,submitted_at=NULL,form_schema_snapshot='{\"fields\":[{\"name\":\"reason\",\"type\":\"text\"}]}' WHERE id=?",editedInstance,editedDraft);
        assertThat(jdbc.queryForObject("SELECT plan_json IS NOT NULL FROM approval_execution WHERE instance_id=?",Boolean.class,instance)).isTrue();

        // This database is verified fixture-owned; simulate the old schema without touching any external deployment.
        jdbc.execute("DROP TABLE approval_execution");
        jdbc.execute("ALTER TABLE workflow_task DROP COLUMN process_node_index, DROP COLUMN process_node_name");
        var replay = jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version::numeric>=202611041230 ORDER BY installed_rank",String.class);
        assertThat(replay).contains("202611041230","202611041240");
        assertThat(jdbc.update("DELETE FROM flyway_schema_history WHERE version::numeric>=202611041230")).isEqualTo(replay.size());
        assertThat(flyway().info().pending()).hasSize(replay.size());
        assertThat(flyway().migrate().migrationsExecuted).isEqualTo(replay.size());
        assertThat(jdbc.queryForObject("SELECT data_json FROM approval_execution WHERE instance_id=?",String.class,instance)).contains("Historic payload");
        assertThat(jdbc.queryForObject("SELECT plan_json IS NULL FROM approval_execution WHERE instance_id=?",Boolean.class,instance)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_execution WHERE instance_id=?",Integer.class,unproven)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_execution WHERE instance_id=?",Integer.class,editedInstance)).isZero();
        jdbc.update("UPDATE approval_application SET data_json='{\"reason\":\"Later payload\"}' WHERE id=?",id);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V202611041230__generic_approval_execution_snapshots.sql"),new ClassPathResource("db/migration/V202611041240__reject_unproven_legacy_approval_snapshots.sql")).execute(datasource);
        assertThat(jdbc.queryForObject("SELECT data_json FROM approval_execution WHERE instance_id=?",String.class,instance)).contains("Historic payload").doesNotContain("Later payload");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approval_execution WHERE instance_id=?",Integer.class,editedInstance)).isZero();
        flyway().validate(); assertThat(flyway().migrate().migrationsExecuted).isZero();

        Map<String,Object> owned = new HashMap<>();
        ENVIRONMENT.register((name,supplier) -> owned.put(name,supplier.get()));
        assertThat(owned.get("spring.datasource.url").toString()).endsWith("/"+ENVIRONMENT.schema());
        owned.put("server.port",0); owned.put("server.address","127.0.0.1"); owned.put("jwt.secret",SECRET);
        owned.put("spring.main.banner-mode","off");
        // Highest-priority properties prevent .env/default YAML from redirecting this restart to a developer database.
        try (var restarted = new SpringApplicationBuilder(AiWorkMateApplication.class)
                .initializers(context -> context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("owned-migration-restart",owned)))
                .run()) {
            var restartedJdbc = restarted.getBean(JdbcTemplate.class);
            ENVIRONMENT.verifyBindings(restartedJdbc,restarted.getBean(StringRedisTemplate.class));
            var restartedFlyway = restarted.getBean(Flyway.class);
            restartedFlyway.validate(); assertThat(restartedFlyway.info().pending()).isEmpty();
            assertThat(restartedFlyway.migrate().migrationsExecuted).isZero();
            assertThat(restartedJdbc.queryForObject("SELECT count(*) FROM approval_execution WHERE instance_id=?",Integer.class,instance)).isOne();
        }
    }
    private Flyway flyway() {
        return Flyway.configure().dataSource(datasource).schemas(ENVIRONMENT.schema()).defaultSchema(ENVIRONMENT.schema()).locations("classpath:db/migration").load();
    }
}
