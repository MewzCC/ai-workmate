package com.aiworkmate.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

/** Developer-owned, eligible direct-approver precondition shared by synthetic write acceptance. */
final class LeaveWriteFixture {
    private LeaveWriteFixture() { }
    static long linkedApprover(JdbcTemplate jdbc,long tenant,long applicant) {
        String role="APR_"+UUID.randomUUID().toString().replace("-","").substring(0,24);
        jdbc.update("INSERT INTO rbac_role(tenant_id,code,name,description,builtin) VALUES (?,?,'Synthetic approver','owned fixture',false)",tenant,role);
        jdbc.update("INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code) VALUES (?,?,'approval:act')",tenant,role);
        long id=jdbc.queryForObject("INSERT INTO app_user(tenant_id,username,password,role) VALUES (?,?,'synthetic fixture',?) RETURNING id",Long.class,tenant,role,role);
        jdbc.update("INSERT INTO user_role(tenant_id,user_id,role_code) VALUES (?,?,?)",tenant,id,role);
        jdbc.update("UPDATE app_user SET approver_user_id=? WHERE tenant_id=? AND id=?",id,tenant,applicant);
        return id;
    }
}
