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

/** Test-owned single-write plans always start unconfirmed; only the real API can authorize enqueueing. */
final class FrozenWriteTaskFixture {
    record Task(long id, String number, String planHash) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AgentHashing hashing;
    private final ToolCatalog catalog;
    FrozenWriteTaskFixture(JdbcTemplate jdbc,ObjectMapper json,AgentHashing hashing,ToolCatalog catalog) {
        this.jdbc=jdbc;this.json=json;this.hashing=hashing;this.catalog=catalog;
    }
    Task create(long tenant,long user,String page,String code,JsonNode args) {
        var definition=catalog.find(code).orElseThrow();
        if(definition.sideEffect()!=SideEffect.SINGLE_WRITE || definition.riskLevel()==RiskLevel.L0)
            throw new IllegalArgumentException("Only confirmed single-write tools are supported");
        var plan=json.createObjectNode().put("planVersion",1);
        plan.putArray("steps").addObject().put("sequence",1).put("toolCode",code)
                .put("toolVersion",definition.handlerVersion()).put("schemaHash",definition.schemaHash())
                .put("argsHash",hashing.hash(args)).put("riskLevel",definition.riskLevel().name())
                .put("sideEffect",definition.sideEffect().name()).put("confirmationPolicy",definition.confirmationPolicy().name());
        String number=UUID.randomUUID().toString(), hash=hashing.hash(plan), trace="write-it-"+number;
        long id=jdbc.queryForObject("""
                INSERT INTO agent_task(task_no,tenant_id,user_id,page_id,input,page_context,plan,plan_hash,
                    max_risk_level,status,timeout_at,trace_id)
                VALUES (?,?,?,?,'isolated confirmed write','{}'::jsonb,?::jsonb,?,?,'WAITING_CONFIRMATION',?,?) RETURNING id
                """,Long.class,number,tenant,user,page,plan.toString(),hash,definition.riskLevel().name(),LocalDateTime.now().plusMinutes(2),trace);
        jdbc.update("""
                INSERT INTO agent_task_step(task_id,sequence_no,tool_code,tool_version,schema_hash,args,args_hash,risk_level,status,trace_id)
                VALUES (?,1,?,?,?,?::jsonb,?,?,'PENDING',?)
                """,id,code,definition.handlerVersion(),definition.schemaHash(),args.toString(),hashing.hash(args),definition.riskLevel().name(),trace);
        return new Task(id,number,hash);
    }
}
