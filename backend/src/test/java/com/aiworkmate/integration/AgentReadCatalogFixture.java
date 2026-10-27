package com.aiworkmate.integration;

import com.aiworkmate.agent.registry.ToolCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Developer-owned database preconditions, never an Agent tool or production service. */
final class AgentReadCatalogFixture {
    record Input(ObjectNode arguments, String evidence) { }
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final long tenant, user;
    private final String label;

    AgentReadCatalogFixture(JdbcTemplate jdbc, ObjectMapper json, long tenant, long user, String label) {
        this.jdbc = jdbc; this.json = json; this.tenant = tenant; this.user = user; this.label = label;
    }

    Input prepare(ToolCode code) {
        ObjectNode args = json.createObjectNode();
        String evidence = label;
        switch (code) {
            case TODO_QUERY, APPROVAL_TASK_QUERY -> {
                long applicant = id("INSERT INTO app_user(tenant_id,username,display_name,password) VALUES (?,?,?,'synthetic fixture') RETURNING id", tenant, label + "-applicant", label);
                LeaveReadFixture.pending(jdbc, tenant, applicant, user);
            }
            case LEAVE_MINE -> {
                long application = LeaveReadFixture.pending(jdbc, tenant, user, user);
                jdbc.update("UPDATE leave_application SET reason=? WHERE id=?", label, application);
            }
            case NOTIFICATION_MINE -> jdbc.update("INSERT INTO notification(tenant_id,user_id,type,title,content) VALUES (?,?,'SYSTEM',?,?)", tenant, user, label, label);
            case USER_PERMISSION_MINE_QUERY -> evidence = "agent:tool:" + code.code();
            case HR_EMPLOYEE_QUERY -> args.put("employeeId", user);
            case HR_ORGANIZATION_QUERY -> {
                long department = id("INSERT INTO department(tenant_id,code,name) VALUES (?,?,?) RETURNING id", tenant, label, label);
                jdbc.update("UPDATE app_user SET department_id=? WHERE id=?", department, user);
                args.put("keyword", label);
            }
            case HR_CHANGE_QUERY -> jdbc.update("INSERT INTO employee_change(tenant_id,employee_user_id,applicant_user_id,review_approver_user_id,change_type,effective_date,reason) VALUES (?,?,?,?,'ONBOARDING',CURRENT_DATE,?)", tenant, user, user, user, label);
            case ATTENDANCE_QUERY -> {
                jdbc.update("INSERT INTO attendance_record(tenant_id,user_id,clock_date,clock_in_time,status) VALUES (?,?,CURRENT_DATE,CURRENT_TIMESTAMP,'NORMAL')", tenant, user);
                args.put("resource", "RECORDS").put("from", LocalDate.now().toString()).put("to", LocalDate.now().toString());
                evidence = LocalDate.now().toString();
            }
            case APPROVAL_CONFIGURATION_QUERY -> { form(); args.put("resource", "FORM").put("keyword", label); }
            case APPROVAL_FORM_DEFINITION_QUERY -> args.put("formId", form());
            case APPROVAL_RULE_DEFINITION_QUERY -> {
                long rule = id("INSERT INTO approval_rule(tenant_id,rule_key,rule_name,rule_type,condition_json,action_json,status,created_by) VALUES (?,?,?,'AMOUNT_THRESHOLD','{\"logic\":\"AND\",\"conditions\":[{\"field\":\"amount\",\"op\":\"gte\",\"value\":\"100\"}]}','{\"appendNode\":\"FINANCE_REVIEW\",\"enabled\":true,\"mode\":\"OR_SIGN\"}','DISABLED',?) RETURNING id", tenant, label, label, user);
                args.put("ruleId", rule);
            }
            case APPROVAL_APPLICATION_MINE_QUERY, APPROVAL_APPLICATION_DETAIL_QUERY, EXPENSE_QUERY -> {
                long form = code == ToolCode.EXPENSE_QUERY
                        ? jdbc.queryForObject("SELECT id FROM approval_form WHERE tenant_id=? AND form_key='expense-application' AND NOT deleted LIMIT 1", Long.class, tenant) : form();
                String key = jdbc.queryForObject("SELECT form_key FROM approval_form WHERE id=?", String.class, form);
                long application = id("INSERT INTO approval_application(tenant_id,applicant_user_id,form_id,form_key,form_name,title,data_json,status) VALUES (?,?,?,?,?,?,?,'DRAFT') RETURNING id",
                        tenant, user, form, key, label, label, json.createObjectNode().put("reason", label).put("purpose", label).put("amount", 12).toString());
                if (code != ToolCode.APPROVAL_APPLICATION_MINE_QUERY) args.put("applicationId", application);
            }
            case ASSET_QUERY -> { jdbc.update("INSERT INTO asset_ledger(tenant_id,asset_code,name,category,owner_user_id) VALUES (?,?,?,'OFFICE',?)", tenant, label, label, user); args.put("keyword", label); }
            case MEETING_ROOM_QUERY -> { room(); args.put("keyword", label); }
            case MEETING_QUERY -> {
                jdbc.update("INSERT INTO meeting_booking(tenant_id,room_id,organizer_user_id,title,start_at,end_at) VALUES (?,?,?,?,?,?)", tenant, room(), user, label, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2));
                args.put("keyword", label);
            }
            case VISITOR_QUERY -> jdbc.update("INSERT INTO visitor_booking(tenant_id,applicant_user_id,host_user_id,visitor_name,purpose,expected_visit_at,submitted_at) VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP)", tenant, user, user, label, label, LocalDateTime.now().plusHours(1));
            case SEAL_QUERY -> jdbc.update("INSERT INTO seal_usage(tenant_id,applicant_user_id,seal_type,document_title,usage_reason,submitted_at) VALUES (?,?,'OFFICIAL',?,?,CURRENT_TIMESTAMP)", tenant, user, label, label);
            case BUDGET_QUERY -> jdbc.update("INSERT INTO budget_plan(tenant_id,budget_code,name,fiscal_year,owner_user_id,owner_label,total_amount,created_by,updated_by) VALUES (?,?,?,?,?,?,100,?,?)", tenant, label, label, LocalDate.now().getYear(), user, label, user, user);
            case CONTRACT_QUERY -> jdbc.update("INSERT INTO business_contract(tenant_id,contract_code,name,contract_type,counterparty_name,owner_user_id,owner_label,amount,start_date,end_date,created_by,updated_by) VALUES (?,?,?,'PURCHASE',?,?,?,100,CURRENT_DATE,CURRENT_DATE+30,?,?)", tenant, label, label, label, user, label, user, user);
            case SUPPLIER_QUERY -> jdbc.update("INSERT INTO supplier(tenant_id,supplier_code,name,category,created_by,updated_by) VALUES (?,?,?,'SERVICE',?,?)", tenant, label, label, user, user);
            case INTEGRATION_ENDPOINT_QUERY -> { endpoint(); args.put("keyword", label); }
            case RUNTIME_LOG_QUERY -> { invocation(); args.put("source", "INTEGRATION").put("keyword", label); }
            case SANDBOX_REPLAY_QUERY -> {
                long invocation = invocation();
                long endpoint = jdbc.queryForObject("SELECT endpoint_id FROM integration_invocation WHERE id=?", Long.class, invocation);
                jdbc.update("INSERT INTO integration_replay_job(tenant_id,source_invocation_id,endpoint_id,endpoint_code,endpoint_name,http_method,relative_path,baseline_request_hash,baseline_outcome,status,trace_id,reason,idempotency_key,requested_by,requested_by_label) VALUES (?,?,?,?,?,'GET','/health',?,'SUCCESS','SUCCESS',?,?,?,?,?)", tenant, invocation, endpoint, label, label, "a".repeat(64), label, label, label, user, label);
                args.put("keyword", label);
            }
            case KNOWLEDGE_BASE_QUERY -> knowledgeBase();
            case KNOWLEDGE_DOCUMENT_QUERY -> {
                long base = knowledgeBase();
                jdbc.update("INSERT INTO knowledge_doc(tenant_id,user_id,kb_id,filename,file_size,file_type,status) VALUES (?,?,?,?,12,'txt','PROCESSING')", tenant, user, base, label + ".txt");
                args.put("kbId", base);
            }
            case KNOWLEDGE_SEARCH -> { args.put("query", label); evidence = null; }
            case DICTIONARY_QUERY, DICTIONARY_ITEM_QUERY -> {
                String typeCode = label.toUpperCase(java.util.Locale.ROOT).replace('-', '_');
                long type = id("INSERT INTO data_dictionary_type(tenant_id,code,name,created_by,updated_by) VALUES (?,?,?,?,?) RETURNING id", tenant, typeCode, label, user, user);
                jdbc.update("INSERT INTO data_dictionary_item(tenant_id,dictionary_type_id,item_value,label,created_by,updated_by) VALUES (?,?,?,?,?,?)", tenant, type, label, label, user, user);
                if (code == ToolCode.DICTIONARY_ITEM_QUERY) args.put("typeCode", typeCode); else args.put("keyword", label);
            }
            case AUDIT_QUERY -> {
                jdbc.update("INSERT INTO business_audit_log(tenant_id,actor_user_id,resource_type,resource_id,action,result,summary,trace_id) VALUES (?,?,?,'fixture','CREATE','SUCCESS',?,?)", tenant, user, label, label, label);
                args.put("resourceType",label);
            }
            case PAGE_ACTION_QUERY -> { args.put("targetPageId", "page-actions"); evidence = "page-actions"; }
            case ACCESS_GOVERNANCE_QUERY -> args.put("filterCode", jdbc.queryForObject("SELECT role FROM app_user WHERE id=?", String.class, user));
            case DATA_PERMISSION_QUERY -> {
                jdbc.update("INSERT INTO data_permission_policy(tenant_id,name,description,scope_type,enabled,created_by) VALUES (?,?,'isolated read fixture','DEPARTMENT_AND_CHILDREN',true,?)",tenant,label,user);
                args.put("scopeType","DEPARTMENT_TREE");
            }
            case AI_PERMISSION_QUERY -> evidence = code.code();
            case TENANT_CONFIGURATION_QUERY -> { evidence = "tenantName"; }
            case SYSTEM_CAPABILITY_QUERY -> { evidence = "redis"; }
            case AGENT_TASK_MINE_QUERY -> { evidence = null; }
            case DASHBOARD_OVERVIEW_QUERY -> {
                LeaveReadFixture.pending(jdbc, tenant, user, user);
                evidence = "metrics";
            }
            case OBSERVABILITY_OVERVIEW_QUERY -> {
                jdbc.update("INSERT INTO platform_operation_log(tenant_id,user_id,actor_label,event_type,http_method,request_path,outcome,status_code,duration_ms,request_id,trace_id,completed_at) VALUES (?,?,?,'HTTP_READ','GET','/api/system/health','SUCCEEDED',200,12,?,?,CURRENT_TIMESTAMP)", tenant, user, label, label, label);
                evidence = "timeline";
            }
            default -> throw new IllegalArgumentException("No real read fixture for " + code.code());
        }
        return new Input(args, evidence);
    }

    Input prepareForPage(ToolCode code, String page) {
        var input = prepare(code);
        if (code != ToolCode.ATTENDANCE_QUERY) return input;
        var args = input.arguments();
        return switch (page) {
            case "attendance-clock" -> new Input(args.put("resource","TODAY"),LocalDate.now().toString());
            case "attendance-exception" -> {
                jdbc.update("UPDATE attendance_record SET status='LATE',late_minutes=10 WHERE tenant_id=? AND user_id=?",tenant,user);
                yield new Input(args.put("resource","EXCEPTIONS"),LocalDate.now().toString());
            }
            case "attendance-reissue" -> {
                jdbc.update("INSERT INTO attendance_reissue(tenant_id,applicant_user_id,clock_date,clock_type,reason) VALUES (?,?,CURRENT_DATE,'CLOCK_IN',?)",tenant,user,label);
                yield new Input(args.put("resource","MY_REISSUES"),label);
            }
            case "attendance-statistics" -> new Input(args.put("resource","STATISTICS").put("year",LocalDate.now().getYear()).put("month",LocalDate.now().getMonthValue()),"statistics");
            case "attendance-settings" -> new Input(args.put("resource","SETTINGS"),"settings");
            default -> input;
        };
    }

    private long form() {
        return id("INSERT INTO approval_form(tenant_id,form_key,form_name,schema_json,status,created_by) VALUES (?,?,?,'{\"fields\":[{\"name\":\"reason\",\"label\":\"Reason\",\"type\":\"textarea\",\"required\":true}]}','DISABLED',?) RETURNING id", tenant, label, label, user);
    }

    private long room() { return id("INSERT INTO meeting_room(tenant_id,code,name,capacity) VALUES (?,?,?,8) RETURNING id", tenant, label, label); }
    private long knowledgeBase() { return id("INSERT INTO knowledge_base(tenant_id,user_id,name) VALUES (?,?,?) RETURNING id", tenant, user, label); }
    private long endpoint() { return id("INSERT INTO integration_endpoint(tenant_id,endpoint_code,name,upstream_code,http_method,relative_path,created_by,updated_by) VALUES (?,?,?,'OA_INTERNAL','GET','/health',?,?) RETURNING id", tenant, label, label, user, user); }
    private long invocation() {
        return id("INSERT INTO integration_invocation(tenant_id,endpoint_id,request_hash,outcome,duration_ms,operator_id,operator_label,trace_id) VALUES (?,?,?,'SUCCESS',12,?,?,?) RETURNING id", tenant, endpoint(), "a".repeat(64), user, label, label);
    }
    private long id(String sql, Object... arguments) { return jdbc.queryForObject(sql, Long.class, arguments); }
}
