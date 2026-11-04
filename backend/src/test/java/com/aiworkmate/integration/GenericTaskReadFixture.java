package com.aiworkmate.integration;

import com.aiworkmate.service.impl.GenericApprovalPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.UUID;

/** Synthetic frozen submission; no tool or production path can create these preconditions. */
final class GenericTaskReadFixture {
    record Submission(long applicationId, long instanceId, long taskId, long formId) { }
    private GenericTaskReadFixture() { }

    static Submission pending(JdbcTemplate jdbc, ObjectMapper json, long tenant, long applicant, long assignee,
                              String title, String schema, String data) {
        try {
            String key = "GTR_" + UUID.randomUUID().toString().replace("-", "");
            long form = jdbc.queryForObject("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status) VALUES (?,?,?,?,'ENABLED') RETURNING id",
                    Long.class, tenant, key, title, schema);
            long application = jdbc.queryForObject("""
                    INSERT INTO approval_application(tenant_id,applicant_user_id,form_id,form_key,form_name,title,data_json,status,submitted_at)
                    VALUES (?,?,?,?,?,?,?,'PENDING',CURRENT_TIMESTAMP) RETURNING id
                    """, Long.class, tenant, applicant, form, key, title, title, data);
            long instance = jdbc.queryForObject("""
                    INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status)
                    VALUES (?,(SELECT id FROM workflow_definition WHERE tenant_id=? AND code='GENERIC_FORM_APPROVAL' ORDER BY version DESC LIMIT 1),
                        'GENERIC_APPROVAL',?,?,'RUNNING') RETURNING id
                    """, Long.class, tenant, tenant, application, applicant);
            jdbc.update("UPDATE approval_application SET workflow_instance_id=? WHERE id=?", instance, application);
            long task = jdbc.queryForObject("""
                    INSERT INTO workflow_task(tenant_id,instance_id,business_type,business_id,assignee_user_id,status,process_node_index,process_node_name)
                    VALUES (?,?,'GENERIC_APPROVAL',?,?,'PENDING',0,'Frozen review node') RETURNING id
                    """, Long.class, tenant, instance, application, assignee);
            String plan = json.writeValueAsString(new GenericApprovalPlan(1, List.of(new GenericApprovalPlan.Step(0, "Frozen review node", assignee, 0))));
            jdbc.update("""
                    INSERT INTO approval_execution(instance_id,tenant_id,application_id,applicant_user_id,form_key,form_name,title,
                        data_json,form_schema,form_version,process_nodes,process_version,rules,plan_json,submitted_at)
                    VALUES (?,?,?,?,?,?,?,?,?,1,'[]',1,'[]',?,CURRENT_TIMESTAMP)
                    """, instance, tenant, application, applicant, key, title, title, data, schema, plan);
            return new Submission(application, instance, task, form);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalidFixture) {
            throw new IllegalArgumentException("Invalid owned generic task fixture", invalidFixture);
        }
    }
}
