package com.aiworkmate.integration;

import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic persisted application and assignment; Agent execution remains read-only. */
final class LeaveReadFixture {
    private LeaveReadFixture() { }

    static long pending(JdbcTemplate jdbc, long tenant, long applicant, long approver) {
        long application = jdbc.queryForObject("""
                INSERT INTO leave_application(tenant_id,applicant_user_id,approver_user_id,leave_type,start_date,
                    start_period,end_date,end_period,duration_half_days,reason,status,submitted_at)
                VALUES (?,?,?,'PERSONAL',CURRENT_DATE,'AM',CURRENT_DATE,'PM',2,'isolated leave','PENDING',CURRENT_TIMESTAMP) RETURNING id
                """, Long.class, tenant, applicant, approver);
        long instance = jdbc.queryForObject("""
                INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status)
                VALUES (?,(SELECT id FROM workflow_definition WHERE tenant_id=? AND code='LEAVE_SINGLE_APPROVAL' LIMIT 1),
                    'LEAVE_APPLICATION',?,?,'RUNNING') RETURNING id
                """, Long.class, tenant, tenant, application, applicant);
        jdbc.update("INSERT INTO workflow_task(tenant_id,instance_id,business_type,business_id,assignee_user_id,status) VALUES (?,?,'LEAVE_APPLICATION',?,?,'PENDING')",
                tenant, instance, application, approver);
        return application;
    }
}
