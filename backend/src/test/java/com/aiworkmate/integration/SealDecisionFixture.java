package com.aiworkmate.integration;

import org.springframework.jdbc.core.JdbcTemplate;

/** Test-owned resources deliberately use different application/task IDs and versions. */
final class SealDecisionFixture {
    record Usage(long id,long instanceId,long taskId) { }
    static Usage pending(JdbcTemplate jdbc,long tenant,long applicant,long approver) {
        long id=jdbc.queryForObject("""
                INSERT INTO seal_usage(tenant_id,applicant_user_id,approver_user_id,seal_type,document_title,usage_reason,
                    copies,status,version,submitted_at)
                VALUES (?,?,?,'OFFICIAL','Owned seal document','Owned purpose',2,'PENDING',5,CURRENT_TIMESTAMP) RETURNING id
                """,Long.class,tenant,applicant,approver);
        long definition=jdbc.queryForObject("SELECT id FROM workflow_definition WHERE tenant_id=? AND business_type='SEAL_USAGE' ORDER BY id LIMIT 1",Long.class,tenant);
        long instance=jdbc.queryForObject("INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status) VALUES (?,?,'SEAL_USAGE',?,?,'RUNNING') RETURNING id",Long.class,tenant,definition,id,applicant);
        jdbc.update("UPDATE seal_usage SET workflow_instance_id=? WHERE id=?",instance,id);
        long task=jdbc.queryForObject("""
                INSERT INTO workflow_task(id,tenant_id,instance_id,business_type,business_id,assignee_user_id,status,version)
                VALUES (nextval('workflow_task_id_seq')+200000,?,?,'SEAL_USAGE',?,?,'PENDING',2) RETURNING id
                """,Long.class,tenant,instance,id,approver);
        return new Usage(id,instance,task);
    }
    static Usage sameNumberedVisitor(JdbcTemplate jdbc,long tenant,long applicant,long approver,long id) {
        jdbc.update("""
                INSERT INTO visitor_booking(id,tenant_id,applicant_user_id,approver_user_id,host_user_id,visitor_name,purpose,
                    expected_visit_at,status,version,submitted_at)
                VALUES (?,?,?,?,?,'Other business visitor','Other business purpose',CURRENT_TIMESTAMP+INTERVAL '1 day','PENDING',5,CURRENT_TIMESTAMP)
                """,id,tenant,applicant,approver,applicant);
        long definition=jdbc.queryForObject("SELECT id FROM workflow_definition WHERE tenant_id=? AND business_type='VISITOR_BOOKING' ORDER BY id LIMIT 1",Long.class,tenant);
        long instance=jdbc.queryForObject("INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status) VALUES (?,?,'VISITOR_BOOKING',?,?,'RUNNING') RETURNING id",Long.class,tenant,definition,id,applicant);
        jdbc.update("UPDATE visitor_booking SET workflow_instance_id=? WHERE id=?",instance,id);
        long task=jdbc.queryForObject("INSERT INTO workflow_task(tenant_id,instance_id,business_type,business_id,assignee_user_id,status,version) VALUES (?,?,'VISITOR_BOOKING',?,?,'PENDING',2) RETURNING id",Long.class,tenant,instance,id,approver);
        return new Usage(id,instance,task);
    }
}
