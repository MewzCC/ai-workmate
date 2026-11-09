package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCatalog;
import com.aiworkmate.agent.task.AgentHashing;
import com.aiworkmate.agent.worker.AgentTaskWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Shared real HTTP confirmation / Gateway test driver for assigned business decisions. */
final class ConfirmedDecisionFixture {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AgentHttpTestFixture http;
    private final AgentTaskWorker worker;
    private final FrozenWriteTaskFixture plans;
    private final long tenant;
    private final String page;
    ConfirmedDecisionFixture(JdbcTemplate jdbc,ObjectMapper json,AgentHttpTestFixture http,AgentTaskWorker worker,
                             AgentHashing hashing,ToolCatalog catalog,long tenant,String page) {
        this.jdbc=jdbc;this.json=json;this.http=http;this.worker=worker;this.tenant=tenant;this.page=page;
        plans=new FrozenWriteTaskFixture(jdbc,json,hashing,catalog);
    }
    FrozenWriteTaskFixture.Task plan(AgentHttpTestFixture.Actor actor,String code,long taskId,int version,String comment) {
        var args=json.createObjectNode().put("taskId",taskId).put("version",version);
        if(comment!=null)args.put("comment",comment);
        return plans.create(tenant,actor.id(),page,code,args);
    }
    String confirm(FrozenWriteTaskFixture.Task task,AgentHttpTestFixture.Actor actor)throws Exception {
        var response=response("POST",path(task)+"/confirmation-token",actor,body(task).toString());
        assertThat(response.statusCode()).isEqualTo(200);return data(response).path("confirmationToken").asText();
    }
    HttpResponse<String> execute(FrozenWriteTaskFixture.Task task,AgentHttpTestFixture.Actor actor,String token)throws Exception {
        var body=body(task);if(token!=null)body.put("confirmationToken",token);
        return response("POST",path(task)+"/execute",actor,body.toString());
    }
    void run(FrozenWriteTaskFixture.Task task,AgentHttpTestFixture.Actor actor,String expected)throws Exception {
        assertThat(execute(task,actor,confirm(task,actor)).statusCode()).isEqualTo(202);terminal(task,expected);
    }
    void terminal(FrozenWriteTaskFixture.Task task,String expected) {
        await().atMost(Duration.ofSeconds(25)).until(()->{worker.poll();return isTerminal(task);});
        assertThat(status(task)).as("owned decision error=%s",jdbc.queryForObject("SELECT error_code FROM agent_task WHERE id=?",String.class,task.id())).isEqualTo(expected);
    }
    String status(FrozenWriteTaskFixture.Task task){return jdbc.queryForObject("SELECT status FROM agent_task WHERE id=?",String.class,task.id());}
    boolean isTerminal(FrozenWriteTaskFixture.Task task){return !List.of("QUEUED","RUNNING").contains(status(task));}
    JsonNode detail(FrozenWriteTaskFixture.Task task,AgentHttpTestFixture.Actor actor)throws Exception{return data(response("GET",path(task),actor,null));}
    void noHandler(FrozenWriteTaskFixture.Task task){assertThat(jdbc.queryForObject("SELECT count(*) FROM agent_tool_invocation WHERE task_id=? AND handler_invoked",Integer.class,task.id())).isZero();}
    HttpResponse<String> response(String method,String path,AgentHttpTestFixture.Actor actor,String body)throws Exception {
        return AgentHttpTestFixture.client().send(http.request(method,path,actor,path.endsWith("/execute")?UUID.randomUUID().toString():null,body).build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode data(HttpResponse<String> response)throws Exception{return json.readTree(response.body()).path("data");}
    private com.fasterxml.jackson.databind.node.ObjectNode body(FrozenWriteTaskFixture.Task task){return json.createObjectNode().put("planVersion",1).put("planHash",task.planHash());}
    private String path(FrozenWriteTaskFixture.Task task){return "/api/ai/tasks/"+task.number();}
}
