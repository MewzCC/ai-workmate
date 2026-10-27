package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.RiskLevel;
import com.aiworkmate.agent.registry.SideEffect;
import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

/** Test-only frozen L0 plan persistence shared by Worker and HTTP acceptance suites. */
final class FrozenReadTaskFixture {
    enum State { PLAN_READY, QUEUED }
    record Task(long id, String number, String planHash) { }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AgentHashing hashing;
    private final ToolCatalog catalog;

    FrozenReadTaskFixture(JdbcTemplate jdbc, ObjectMapper json, AgentHashing hashing, ToolCatalog catalog) {
        this.jdbc = jdbc;
        this.json = json;
        this.hashing = hashing;
        this.catalog = catalog;
    }

    Task create(long tenant, long user, String page, String code, JsonNode arguments, State state) {
        var definition = catalog.find(code).orElseThrow();
        if (definition.riskLevel() != RiskLevel.L0 || definition.sideEffect() != SideEffect.NONE) {
            throw new IllegalArgumentException("This fixture only supports L0 read plans");
        }
        var step = json.createObjectNode().put("sequence", 1).put("toolCode", code)
                .put("toolVersion", definition.handlerVersion()).put("schemaHash", definition.schemaHash())
                .put("argsHash", hashing.hash(arguments)).put("riskLevel", definition.riskLevel().name())
                .put("sideEffect", definition.sideEffect().name()).put("confirmationPolicy", definition.confirmationPolicy().name());
        var plan = json.createObjectNode().put("planVersion", 1);
        plan.putArray("steps").add(step);
        String number = UUID.randomUUID().toString();
        String hash = hashing.hash(plan);
        Long id = jdbc.queryForObject("""
                INSERT INTO agent_task(task_no,tenant_id,user_id,page_id,input,page_context,plan,plan_hash,
                    status,timeout_at,trace_id)
                VALUES (?,?,?,?, 'isolated read execution','{}'::jsonb,?::jsonb,?,?,?,?) RETURNING id
                """, Long.class, number, tenant, user, page, plan.toString(), hash, state.name(),
                LocalDateTime.now().plusMinutes(2), "read-it-" + number);
        jdbc.update("""
                INSERT INTO agent_task_step(task_id,sequence_no,tool_code,tool_version,schema_hash,args,args_hash,risk_level,status,trace_id)
                VALUES (?,1,?,?,?,?::jsonb,?,'L0','PENDING',?)
                """, id, code, definition.handlerVersion(), definition.schemaHash(), arguments.toString(), hashing.hash(arguments), "read-it-" + number);
        return new Task(id, number, hash);
    }
}
