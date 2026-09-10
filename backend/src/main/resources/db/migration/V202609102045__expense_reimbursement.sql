UPDATE approval_form
SET description = '差旅及日常费用报销申请单模板',
    schema_json = '{"fields":[{"name":"amount","label":"报销金额","type":"number","required":true,"min":0.01},{"name":"category","label":"费用类型","type":"select","required":true,"options":["TRAVEL","MEAL","TRANSPORT","OFFICE","OTHER"]},{"name":"expenseDate","label":"费用日期","type":"date","required":true},{"name":"invoiceNumber","label":"发票号码","type":"text","required":true},{"name":"reason","label":"报销事由","type":"textarea","required":true}]}',
    version = version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE form_key = 'expense-application'
  AND schema_json NOT LIKE '%expenseDate%';

INSERT INTO approval_process(
    tenant_id, process_key, process_name, description, form_id, node_json,
    status, version, created_by, created_at, updated_at, deleted
)
SELECT form.tenant_id, 'expense-single-approval', '费用报销审批',
       '直属上级审批，审批规则可按金额追加财务复核节点', form.id,
       '[{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]',
       'ENABLED', 1, form.created_by, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE
FROM approval_form form
WHERE form.form_key = 'expense-application' AND form.deleted = FALSE
ON CONFLICT (tenant_id, process_key) DO UPDATE SET
    form_id = EXCLUDED.form_id,
    description = EXCLUDED.description,
    node_json = EXCLUDED.node_json,
    status = 'ENABLED',
    deleted = FALSE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT role.tenant_id, role.code, 'route:expense'
FROM rbac_role role
WHERE role.code IN ('SUPER_ADMIN', 'FINANCE_ADMIN', 'EMPLOYEE')
ON CONFLICT DO NOTHING;

UPDATE rbac_route
SET component_key = 'EXPENSE', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'expense';
