package com.aiworkmate.integration;

import org.springframework.jdbc.core.JdbcTemplate;

/** Different resource/task IDs and versions prevent an accidental booking-version decision from passing. */
final class VisitorDecisionFixture {
    record Booking(long id,long instanceId,long taskId) { }
    static Booking pending(JdbcTemplate jdbc,long tenant,long applicant,long approver) {
        long id=jdbc.queryForObject("""
                INSERT INTO visitor_booking(tenant_id,applicant_user_id,approver_user_id,host_user_id,visitor_name,purpose,
                    expected_visit_at,status,version,submitted_at)
                VALUES (?,?,?,?,'Owned visitor','Owned purpose',CURRENT_TIMESTAMP+INTERVAL '1 day','PENDING',5,CURRENT_TIMESTAMP) RETURNING id
                """,Long.class,tenant,applicant,approver,applicant);
        long definition=jdbc.queryForObject("SELECT id FROM workflow_definition WHERE tenant_id=? AND business_type='VISITOR_BOOKING' ORDER BY id LIMIT 1",Long.class,tenant);
        long instance=jdbc.queryForObject("""
                INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status)
                VALUES (?,?,'VISITOR_BOOKING',?,?,'RUNNING') RETURNING id
                """,Long.class,tenant,definition,id,applicant);
        jdbc.update("UPDATE visitor_booking SET workflow_instance_id=? WHERE id=?",instance,id);
        long task=jdbc.queryForObject("""
                INSERT INTO workflow_task(id,tenant_id,instance_id,business_type,business_id,assignee_user_id,status,version)
                VALUES (nextval('workflow_task_id_seq')+100000,?,?,'VISITOR_BOOKING',?,?,'PENDING',2) RETURNING id
                """,Long.class,tenant,instance,id,approver);
        return new Booking(id,instance,task);
    }
}
