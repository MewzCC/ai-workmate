-- Local layout fixtures. Run with psql -v owner_id=<local user id> -f ... .
-- The prefix includes the owner ID. All inserts and workflow links commit together.
\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(178420, 211);
CREATE TEMP TABLE demo ON COMMIT DROP AS
SELECT n, a.tenant_id AS t, a.id AS u, 'OADEMO-'||a.id||'-'||lpad(n::text,3,'0') AS code,
       '【演示】'||lpad(n::text,3,'0') AS label,
       ('test-'||a.id||'-'||lpad(n::text,3,'0')||'@fixture.invalid') AS email,
       coalesce(a.display_name,a.username) AS owner_label
FROM app_user a CROSS JOIN generate_series(1,48) n
WHERE a.id=:'owner_id'::bigint AND current_database()='ai_workmate_dev';
DO $$ BEGIN
  IF (SELECT count(*) FROM demo) <> 48 THEN RAISE EXCEPTION 'Expected local development database and valid owner_id'; END IF;
END $$;

INSERT INTO department (tenant_id,code,name)
SELECT s.t,s.code,s.label||'项目组'
FROM (select * from demo where n<=8) s
WHERE NOT EXISTS (SELECT 1 FROM department x WHERE x.tenant_id=s.t AND x.code=s.code);

INSERT INTO position (tenant_id,code,name)
SELECT s.t,s.code,s.label||'专员'
FROM (select * from demo where n<=8) s
WHERE NOT EXISTS (SELECT 1 FROM position x WHERE x.tenant_id=s.t AND x.code=s.code);

INSERT INTO app_user (tenant_id,username,email,display_name,password,role,status,employment_status,department_id,position_id,approver_user_id)
SELECT s.t,lower(s.code),s.email,s.label||'成员','!fixture-no-login','EMPLOYEE',1,'ACTIVE',d.id,p.id,s.u
FROM demo s JOIN demo group_seed ON group_seed.n=(s.n-1)%8+1
JOIN department d ON d.tenant_id=s.t AND d.code=group_seed.code
JOIN position p ON p.tenant_id=s.t AND p.code=group_seed.code
WHERE NOT EXISTS (SELECT 1 FROM app_user x WHERE x.tenant_id=s.t AND x.email=s.email);

CREATE TEMP VIEW demo_staff AS SELECT s.*, a.id AS emp, a.department_id AS dept, a.position_id AS pos, a.created_at::date AS day FROM demo s JOIN app_user a ON a.tenant_id=s.t AND a.email=s.email AND a.username=lower(s.code);

INSERT INTO user_role (tenant_id,user_id,role_code)
SELECT s.t,s.emp,'EMPLOYEE'
FROM demo_staff s
WHERE NOT EXISTS (SELECT 1 FROM user_role x WHERE x.tenant_id=s.t AND x.user_id=s.emp AND x.role_code='EMPLOYEE');

INSERT INTO attendance_record (tenant_id,user_id,clock_date,clock_in_time,clock_out_time,status,late_minutes,source)
SELECT s.t,s.emp,s.day-k,s.day-k+time '09:00'+(CASE WHEN (s.n+k)%3=0 THEN interval '16 min' ELSE interval '0' END),s.day-k+time '18:00',CASE WHEN (s.n+k)%3=0 THEN 'LATE' ELSE 'NORMAL' END,CASE WHEN (s.n+k)%3=0 THEN 16 ELSE 0 END,'WEB'
FROM demo_staff s CROSS JOIN generate_series(1,20) k
WHERE NOT EXISTS (SELECT 1 FROM attendance_record x WHERE x.tenant_id=s.t AND x.user_id=s.emp AND x.clock_date=s.day-k);

INSERT INTO attendance_reissue (tenant_id,applicant_user_id,approver_user_id,clock_date,clock_type,reason,status,submitted_at)
SELECT s.t,s.u,s.emp,s.day-1-(s.n/2),CASE WHEN s.n%2=0 THEN 'CLOCK_IN' ELSE 'CLOCK_OUT' END,s.label||'外勤补卡','PENDING',now()
FROM demo_staff s
WHERE NOT EXISTS (SELECT 1 FROM attendance_reissue x WHERE x.tenant_id=s.t AND x.applicant_user_id=s.u AND x.reason=s.label||'外勤补卡');

INSERT INTO employee_change (tenant_id,employee_user_id,applicant_user_id,review_approver_user_id,change_type,effective_date,current_department_id,target_department_id,reason,status,submitted_at)
SELECT s.t,s.emp,s.emp,s.u,'TRANSFER',s.day+14,s.dept,d.id,s.label||'调岗申请','PENDING',now()
FROM demo_staff s JOIN demo g ON g.n=s.n%8+1 JOIN department d ON d.code=g.code AND d.tenant_id=s.t
WHERE NOT EXISTS (SELECT 1 FROM employee_change x WHERE x.tenant_id=s.t AND x.employee_user_id=s.emp AND x.reason=s.label||'调岗申请');

INSERT INTO budget_plan (tenant_id,budget_code,name,fiscal_year,owner_user_id,owner_label,total_amount,warning_threshold,status,summary,created_by,updated_by)
SELECT s.t,s.code,s.label||'项目预算',extract(year from current_date),s.u,s.owner_label,10000+500*s.n,80,CASE WHEN s.n%5=0 THEN 'DRAFT' ELSE 'ACTIVE' END,'本地布局测试',s.u,s.u
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM budget_plan x WHERE x.tenant_id=s.t AND x.budget_code=s.code);

INSERT INTO supplier (tenant_id,supplier_code,name,category,supplier_level,status,contact_name,contact_email,created_by,updated_by)
SELECT s.t,s.code,s.label||'供应商',(ARRAY['MATERIAL','SERVICE','LOGISTICS','CONSULTING'])[1+s.n%4],'STANDARD',CASE WHEN s.n%5=0 THEN 'DRAFT' ELSE 'ACTIVE' END,'演示联系人',s.email,s.u,s.u
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM supplier x WHERE x.tenant_id=s.t AND x.supplier_code=s.code);

INSERT INTO business_contract (tenant_id,contract_code,name,contract_type,counterparty_name,supplier_id,supplier_label,owner_user_id,owner_label,amount,start_date,end_date,status,summary,created_by,updated_by)
SELECT s.t,s.code,s.label||'服务合同','SERVICE',v.name,v.id,v.name,s.u,s.owner_label,1000+80*s.n,current_date,current_date+90,CASE WHEN s.n%5=0 THEN 'DRAFT' ELSE 'ACTIVE' END,'本地布局测试',s.u,s.u
FROM demo s JOIN supplier v ON v.tenant_id=s.t AND v.supplier_code=s.code
WHERE NOT EXISTS (SELECT 1 FROM business_contract x WHERE x.tenant_id=s.t AND x.contract_code=s.code);

INSERT INTO asset_ledger (tenant_id,asset_code,name,category,status,department_id,owner_user_id,purchase_date,original_value,remark)
SELECT s.t,s.code,s.label||'笔记本电脑','电子设备','IN_USE',s.dept,s.emp,s.day-30,4000+s.n*25,'本地布局测试'
FROM demo_staff s
WHERE NOT EXISTS (SELECT 1 FROM asset_ledger x WHERE x.tenant_id=s.t AND x.asset_code=s.code);

INSERT INTO meeting_room (tenant_id,code,name,location,capacity,facilities,status)
SELECT s.t,s.code,s.label||'会议室','测试楼 '||(s.n%5+1)||' 层',4+s.n%16,'投影、白板','OPEN'
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM meeting_room x WHERE x.tenant_id=s.t AND x.code=s.code);

INSERT INTO meeting_booking (tenant_id,room_id,organizer_user_id,title,agenda,start_at,end_at,attendee_count,status)
SELECT s.t,r.id,s.u,s.label||'项目例会','演示议程',s.day+s.n%5+time '10:00',s.day+s.n%5+time '11:00',3,'BOOKED'
FROM demo_staff s JOIN meeting_room r ON r.tenant_id=s.t AND r.code=s.code
WHERE NOT EXISTS (SELECT 1 FROM meeting_booking x WHERE x.tenant_id=s.t AND x.room_id=r.id AND x.title=s.label||'项目例会');

INSERT INTO notification (tenant_id,user_id,type,title,content,read_flag)
SELECT s.t,s.u,'system',s.label||'测试通知','用于检查通知列表、阅读详情及分页。',true
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM notification x WHERE x.tenant_id=s.t AND x.user_id=s.u AND x.title=s.label||'测试通知');

INSERT INTO conversation (tenant_id,user_id,title,model)
SELECT s.t,s.u,s.label||'测试会话','default'
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM conversation x WHERE x.tenant_id=s.t AND x.user_id=s.u AND x.title=s.label||'测试会话');

INSERT INTO message (conversation_id,role,content,status)
SELECT c.id,'user','这是一条用于验证会话布局的测试消息。','completed'
FROM demo s JOIN conversation c ON c.tenant_id=s.t AND c.user_id=s.u AND c.title=s.label||'测试会话'
WHERE NOT EXISTS (SELECT 1 FROM message x WHERE x.conversation_id=c.id AND x.role='user' AND x.content='这是一条用于验证会话布局的测试消息。');

INSERT INTO agent_task (tenant_id,user_id,task_no,page_id,input,status,trace_id,max_risk_level,finished_at)
SELECT s.t,s.u,s.code,'dashboard',s.label||'离线任务样例','CANCELLED',s.code,'L0',now()
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM agent_task x WHERE x.tenant_id=s.t AND x.task_no=s.code);

INSERT INTO knowledge_base (tenant_id,user_id,name,description)
SELECT s.t,s.u,s.label||'项目知识库','用于检查知识库卡片、搜索和设置。'
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM knowledge_base x WHERE x.tenant_id=s.t AND x.user_id=s.u AND x.name=s.label||'项目知识库');

INSERT INTO data_permission_policy (tenant_id,name,description,scope_type,enabled,created_by)
SELECT s.t,s.label||'个人范围','测试策略，仅供演示','SELF',false,s.u
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM data_permission_policy x WHERE x.tenant_id=s.t AND x.name=s.label||'个人范围');

INSERT INTO rbac_role (tenant_id,code,name,description,builtin)
SELECT s.t,replace(s.code,'-','_'),s.label||'测试角色','无授权的测试角色',false
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM rbac_role x WHERE x.tenant_id=s.t AND x.code=replace(s.code,'-','_'));

INSERT INTO data_dictionary_type (tenant_id,code,name,status,created_by,updated_by)
SELECT s.t,replace(s.code,'-','_'),s.label||'测试分类','ACTIVE',s.u,s.u
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM data_dictionary_type x WHERE x.tenant_id=s.t AND x.code=replace(s.code,'-','_'));

INSERT INTO data_dictionary_item (tenant_id,dictionary_type_id,item_value,label,status,sort_order,created_by,updated_by)
SELECT s.t,d.id,'item_'||k,'演示选项 '||k,'ACTIVE',k,s.u,s.u
FROM demo s JOIN data_dictionary_type d ON d.tenant_id=s.t AND d.code=replace(s.code,'-','_') CROSS JOIN generate_series(1,24) k
WHERE NOT EXISTS (SELECT 1 FROM data_dictionary_item x WHERE x.tenant_id=s.t AND x.dictionary_type_id=d.id AND x.item_value='item_'||k);

INSERT INTO integration_endpoint (tenant_id,endpoint_code,name,upstream_code,http_method,relative_path,status,description,created_by,updated_by)
SELECT s.t,s.code,s.label||'测试接口','DEMO','GET','/demo/'||s.n,'DISABLED','未连接上游的展示样例',s.u,s.u
FROM demo s
WHERE NOT EXISTS (SELECT 1 FROM integration_endpoint x WHERE x.tenant_id=s.t AND x.endpoint_code=s.code);

-- Disabled rules never participate in live approval routing.
INSERT INTO approval_form(tenant_id,form_key,form_name,description,schema_json,status,created_by)
SELECT s.t,lower(s.code),s.label||'费用申请表','演示模板',base.schema_json,'ENABLED',s.u
FROM demo s JOIN approval_form base ON base.tenant_id=s.t AND base.form_key='expense-application' AND NOT base.deleted
WHERE NOT EXISTS (SELECT 1 FROM approval_form f WHERE f.tenant_id=s.t AND f.form_key=lower(s.code));

INSERT INTO approval_process(tenant_id,process_key,process_name,description,form_id,node_json,status,created_by)
SELECT s.t,lower(s.code),s.label||'审批流程','演示流程',f.id,base.node_json,base.status,s.u
FROM demo s JOIN approval_form f ON f.tenant_id=s.t AND f.form_key=lower(s.code)
JOIN LATERAL (SELECT p.* FROM approval_process p WHERE p.tenant_id=s.t AND NOT p.deleted AND p.process_key NOT LIKE 'oademo-%' ORDER BY p.id LIMIT 1) base ON true
WHERE NOT EXISTS (SELECT 1 FROM approval_process p WHERE p.tenant_id=s.t AND p.process_key=lower(s.code));

INSERT INTO approval_rule(tenant_id,rule_key,rule_name,rule_type,priority,condition_json,action_json,description,status,created_by)
SELECT s.t,lower(s.code),s.label||'审批规则',base.rule_type,base.priority,base.condition_json,base.action_json,'演示规则','DISABLED',s.u
FROM demo s JOIN LATERAL (SELECT r.* FROM approval_rule r WHERE r.tenant_id=s.t AND NOT r.deleted AND r.rule_key NOT LIKE 'oademo-%' ORDER BY r.id LIMIT 1) base ON true
WHERE NOT EXISTS (SELECT 1 FROM approval_rule r WHERE r.tenant_id=s.t AND r.rule_key=lower(s.code));

INSERT INTO approval_application(tenant_id,applicant_user_id,form_id,form_key,form_name,title,data_json,status,form_schema_snapshot,form_version_snapshot)
SELECT s.t,s.u,f.id,f.form_key,f.form_name,s.label||'费用报销',
       jsonb_build_object('category','TRAVEL','amount',100+s.n*10,'expenseDate',s.day,'reason',s.label||'差旅报销')::text,
       'DRAFT',f.schema_json,f.version
FROM demo_staff s JOIN approval_form f ON f.tenant_id=s.t AND f.form_key='expense-application' AND NOT f.deleted
WHERE NOT EXISTS (SELECT 1 FROM approval_application a WHERE a.tenant_id=s.t AND a.applicant_user_id=s.u AND a.title=s.label||'费用报销');

-- Each personal or assigned request has a real workflow instance and task linked to that request.
CREATE TEMP VIEW demo_requests AS
SELECT s.*, side, CASE WHEN side='个人' THEN u ELSE emp END AS applicant,
       CASE WHEN side='个人' THEN emp ELSE u END AS reviewer,
       label||side AS subject,
       (ARRAY['PENDING','PENDING','APPROVED','REJECTED'])[(n-1)%4+1] AS state
FROM demo_staff s CROSS JOIN (VALUES ('个人'),('待办')) v(side);

INSERT INTO leave_application(tenant_id, applicant_user_id,approver_user_id,leave_type,start_date,start_period,end_date,end_period,duration_half_days,reason,status,submitted_at,completed_at)
SELECT t,applicant,reviewer,'ANNUAL',day+n,'AM',day+n,'PM',2,subject||'请假',state,now()-interval '1 day',CASE WHEN state<>'PENDING' THEN now() END
FROM demo_requests s WHERE NOT EXISTS (SELECT 1 FROM leave_application a WHERE a.tenant_id=s.t AND a.applicant_user_id=s.applicant AND a.reason=s.subject||'请假');

INSERT INTO visitor_booking(tenant_id,applicant_user_id,approver_user_id,visitor_name,visitor_company,purpose,host_user_id,expected_visit_at,expected_leave_at,party_size,status,submitted_at,completed_at)
SELECT t,applicant,reviewer,label||'访客','演示单位',subject||'来访',reviewer,day+n+time '10:00',day+n+time '11:00',1,state,now()-interval '1 day',CASE WHEN state<>'PENDING' THEN now() END
FROM demo_requests s WHERE NOT EXISTS (SELECT 1 FROM visitor_booking v WHERE v.tenant_id=s.t AND v.applicant_user_id=s.applicant AND v.purpose=s.subject||'来访');

INSERT INTO seal_usage(tenant_id,applicant_user_id,approver_user_id,seal_type,document_title,usage_reason,copies,status,submitted_at,completed_at)
SELECT t,applicant,reviewer,'CONTRACT',subject||'用印','演示合同盖章',1,state,now()-interval '1 day',CASE WHEN state<>'PENDING' THEN now() END
FROM demo_requests s WHERE NOT EXISTS (SELECT 1 FROM seal_usage v WHERE v.tenant_id=s.t AND v.applicant_user_id=s.applicant AND v.document_title=s.subject||'用印');

CREATE TEMP VIEW demo_workflow AS
SELECT s.*,b.kind,b.id AS bid,b.submitted_at,b.completed_at
FROM demo_requests s JOIN LATERAL (
  SELECT 'LEAVE_APPLICATION' AS kind,id,submitted_at,completed_at FROM leave_application WHERE tenant_id=s.t AND applicant_user_id=s.applicant AND reason=s.subject||'请假'
  UNION ALL SELECT 'VISITOR_BOOKING',id,submitted_at,completed_at FROM visitor_booking WHERE tenant_id=s.t AND applicant_user_id=s.applicant AND purpose=s.subject||'来访'
  UNION ALL SELECT 'SEAL_USAGE',id,submitted_at,completed_at FROM seal_usage WHERE tenant_id=s.t AND applicant_user_id=s.applicant AND document_title=s.subject||'用印'
) b ON true;

INSERT INTO workflow_instance(tenant_id,definition_id,business_type,business_id,applicant_id,status,started_at,completed_at)
SELECT s.t,d.id,s.kind,s.bid,s.applicant,CASE WHEN s.state='PENDING' THEN 'RUNNING' ELSE 'COMPLETED' END,s.submitted_at,s.completed_at
FROM demo_workflow s JOIN workflow_definition d ON d.tenant_id=s.t AND d.business_type=s.kind AND d.version=1
WHERE NOT EXISTS (SELECT 1 FROM workflow_instance i WHERE i.tenant_id=s.t AND i.business_type=s.kind AND i.business_id=s.bid);
CREATE TEMP VIEW demo_instances AS SELECT s.*,i.id AS iid FROM demo_workflow s JOIN workflow_instance i ON i.tenant_id=s.t AND i.business_type=s.kind AND i.business_id=s.bid;

INSERT INTO workflow_task(tenant_id,instance_id,business_type,business_id,assignee_user_id,status,due_at,completed_at,created_at)
SELECT t,iid,kind,bid,reviewer,state,submitted_at+interval '3 days',completed_at,submitted_at FROM demo_instances s
WHERE NOT EXISTS (SELECT 1 FROM workflow_task w WHERE w.tenant_id=s.t AND w.instance_id=s.iid);

UPDATE leave_application b SET workflow_instance_id=s.iid FROM demo_instances s
WHERE s.kind='LEAVE_APPLICATION' AND b.tenant_id=s.t AND b.id=s.bid AND b.workflow_instance_id IS NULL;
UPDATE visitor_booking b SET workflow_instance_id=s.iid FROM demo_instances s
WHERE s.kind='VISITOR_BOOKING' AND b.tenant_id=s.t AND b.id=s.bid AND b.workflow_instance_id IS NULL;
UPDATE seal_usage b SET workflow_instance_id=s.iid FROM demo_instances s
WHERE s.kind='SEAL_USAGE' AND b.tenant_id=s.t AND b.id=s.bid AND b.workflow_instance_id IS NULL;

INSERT INTO business_audit_log(tenant_id,actor_user_id,resource_type,resource_id,action,result,summary,trace_id)
SELECT s.t,s.u,'BUDGET',b.id::text,'TEST_DATA_SEED','SUCCESS','本地测试数据初始化：创建演示预算',s.code
FROM demo s JOIN budget_plan b ON b.tenant_id=s.t AND b.budget_code=s.code
WHERE NOT EXISTS (SELECT 1 FROM business_audit_log a WHERE a.tenant_id=s.t AND a.trace_id=s.code AND a.action='TEST_DATA_SEED');

INSERT INTO data_permission_role_binding(tenant_id,role_code,policy_id,updated_by)
SELECT s.t,r.code,p.id,s.u FROM demo s JOIN rbac_role r ON r.tenant_id=s.t AND r.code=replace(s.code,'-','_')
JOIN data_permission_policy p ON p.tenant_id=s.t AND p.scope_type='SELF' AND p.enabled AND p.name NOT LIKE '【演示】%'
WHERE NOT EXISTS (SELECT 1 FROM data_permission_role_binding b WHERE b.tenant_id=s.t AND b.role_code=r.code)
ON CONFLICT DO NOTHING;

-- Reserve today's clock for interactive testing. The documentation-only IP marks fixture punches.
INSERT INTO attendance_record(tenant_id,user_id,clock_date,clock_in_time,clock_out_time,clock_in_ip,clock_out_ip,status,source)
SELECT t,u,day-n,day-n+time '09:00',day-n+time '18:00','192.0.2.42','192.0.2.42','NORMAL','API'
FROM demo_staff s WHERE extract(isodow from day-n)<=5
AND NOT EXISTS (SELECT 1 FROM attendance_record a WHERE a.tenant_id=s.t AND a.user_id=s.u AND a.clock_date=s.day-s.n);

-- Offline failure samples have no successful upstream call or replay and cannot be scheduled.
INSERT INTO integration_invocation(tenant_id,endpoint_id,request_hash,outcome,duration_ms,response_preview,error_code,operator_id,operator_label,trace_id)
SELECT s.t,e.id,encode(sha256(convert_to(s.code,'UTF8')),'hex'),'FAILED',0,'本地展示样例，未请求上游','DEMO_OFFLINE',s.u,'本地演示',s.code
FROM demo s JOIN integration_endpoint e ON e.tenant_id=s.t AND e.endpoint_code=s.code
WHERE NOT EXISTS (SELECT 1 FROM integration_invocation i WHERE i.tenant_id=s.t AND i.endpoint_id=e.id AND i.trace_id=s.code);

INSERT INTO integration_replay_job(tenant_id,source_invocation_id,endpoint_id,endpoint_code,endpoint_name,http_method,relative_path,
    baseline_request_hash,baseline_outcome,baseline_response_preview,status,replay_duration_ms,replay_error_code,trace_id,reason,
    idempotency_key,requested_by,requested_by_label,completed_at)
SELECT s.t,i.id,e.id,e.endpoint_code,e.name,e.http_method,e.relative_path,i.request_hash,i.outcome,i.response_preview,
       'FAILED',0,'DEMO_OFFLINE',s.code,s.label||'离线展示记录，未执行回放',s.code,s.u,'本地演示',now()
FROM demo s JOIN integration_endpoint e ON e.tenant_id=s.t AND e.endpoint_code=s.code
JOIN integration_invocation i ON i.endpoint_id=e.id AND i.trace_id=s.code AND i.tenant_id=s.t
WHERE NOT EXISTS (SELECT 1 FROM integration_replay_job j WHERE j.tenant_id=s.t AND j.idempotency_key=s.code);

-- Exercise the document list without reporting a successful index or sending data to a paid model.
INSERT INTO knowledge_doc(tenant_id,user_id,kb_id,filename,file_type,file_size,chunk_count,status,error_message)
SELECT s.t,s.u,k.id,'演示说明-'||lpad(doc.n::text,3,'0')||'.txt','txt',0,0,'FAILED','离线展示样例，未上传或建立索引'
FROM demo s JOIN knowledge_base k ON k.tenant_id=s.t AND k.user_id=s.u AND k.name=s.label||'项目知识库'
CROSS JOIN generate_series(1,24) doc(n)
WHERE NOT EXISTS (SELECT 1 FROM knowledge_doc d WHERE d.kb_id=k.id AND d.filename='演示说明-'||lpad(doc.n::text,3,'0')||'.txt');

-- Test audit is separated by an explicit trace and action. Do not rewrite existing users' settings or credentials.
COMMIT;
