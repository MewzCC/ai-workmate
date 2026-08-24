-- ============================================================
-- 流程审批信息架构重构 + 发起审批模板种子
-- ------------------------------------------------------------
-- 1) 「流程审批」按新 IA 调整页面名称：审批中心 / 表单管理 / 流程列表；
-- 2) 新增「流程管理」（MENU）目录，收纳 流程列表 / 表单管理 / 审批规则；
-- 3) 新增「发起审批」路由（APPROVAL_START），EMPLOYEE 及以上角色可见模板中心；
-- 4) 补充演示模板种子：出差 / 加班 / 采购 / 付款（表单 + 流程，幂等）。
-- 全部幂等，首次执行与重复执行均安全。
-- ============================================================

-- ---------- 1. 页面改名（IA 对齐） ----------
UPDATE rbac_route
SET name = '审批中心', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'approval-list';

UPDATE rbac_route
SET name = '表单管理', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'form-engine';

UPDATE rbac_route
SET name = '流程列表', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'process-config';

-- 审批规则名称保持不变

-- ---------- 2. 流程管理目录（收纳三个配置页面） ----------
INSERT INTO rbac_route(route_key, parent_key, name, path, icon, route_type, component_key, permission_code, sort_order)
VALUES ('process-manage', 'approval', '流程管理', NULL, NULL, 'MENU', NULL, NULL, 3)
ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key, name = EXCLUDED.name, sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

UPDATE rbac_route
SET parent_key = 'process-manage', updated_at = CURRENT_TIMESTAMP
WHERE route_key IN ('form-engine', 'process-config', 'approval-rules');

-- ---------- 3. 发起审批路由（模板中心，全员可见） ----------
INSERT INTO rbac_permission(code, name, module, description)
VALUES ('route:approval-start', '访问发起审批', '页面访问', '允许访问发起审批模板中心')
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_route(route_key, parent_key, name, path, icon, route_type, component_key, permission_code, sort_order)
VALUES ('approval-start', 'approval', '发起审批', '/oa/approval-start', NULL, 'PAGE', 'APPROVAL_START', 'route:approval-start', 2)
ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key, name = EXCLUDED.name, path = EXCLUDED.path,
    route_type = EXCLUDED.route_type, component_key = EXCLUDED.component_key,
    permission_code = EXCLUDED.permission_code, sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_role_permission(role_code, permission_code)
SELECT role_code, 'route:approval-start'
FROM (VALUES ('EMPLOYEE'), ('SYSTEM_ADMIN'), ('PROCESS_ADMIN'), ('FINANCE_ADMIN')) AS roles(role_code)
ON CONFLICT DO NOTHING;

-- ---------- 4. 演示模板种子（表单 + 关联流程） ----------

INSERT INTO approval_form (tenant_id, form_key, form_name, description, schema_json)
SELECT id, 'business-trip', '出差申请单', '出差行程与预算申请单模板',
       '{"fields":[{"name":"city","label":"出差城市","type":"text"},{"name":"startDate","label":"开始日期","type":"date"},{"name":"endDate","label":"结束日期","type":"date"},{"name":"budget","label":"预算金额","type":"number"},{"name":"reason","label":"出差事由","type":"textarea"}]}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, form_key) DO NOTHING;

INSERT INTO approval_form (tenant_id, form_key, form_name, description, schema_json)
SELECT id, 'overtime', '加班申请单', '加班时间段与补偿方式申请单模板',
       '{"fields":[{"name":"workDate","label":"加班日期","type":"date"},{"name":"startTime","label":"开始时间","type":"time"},{"name":"endTime","label":"结束时间","type":"time"},{"name":"hours","label":"加班时长","type":"number"},{"name":"reason","label":"加班事由","type":"textarea"}]}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, form_key) DO NOTHING;

INSERT INTO approval_form (tenant_id, form_key, form_name, description, schema_json)
SELECT id, 'purchase', '采购申请单', '办公物资采购申请单模板',
       '{"fields":[{"name":"itemName","label":"物品名称","type":"text"},{"name":"amount","label":"采购金额","type":"number"},{"name":"supplier","label":"供应商","type":"text"},{"name":"reason","label":"采购事由","type":"textarea"}]}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, form_key) DO NOTHING;

INSERT INTO approval_form (tenant_id, form_key, form_name, description, schema_json)
SELECT id, 'payment', '付款申请单', '合同付款与供应商付款申请单模板',
       '{"fields":[{"name":"payee","label":"收款方","type":"text"},{"name":"amount","label":"付款金额","type":"number"},{"name":"bankName","label":"开户行","type":"text"},{"name":"contractNo","label":"关联合同号","type":"text"},{"name":"reason","label":"付款事由","type":"textarea"}]}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, form_key) DO NOTHING;

INSERT INTO approval_process (tenant_id, process_key, process_name, description, form_id, node_json)
SELECT id, 'business-trip-approval', '出差单级审批', '直属上级单级审批',
       (SELECT f.id FROM approval_form f WHERE f.tenant_id = t.id AND f.form_key = 'business-trip'),
       '[{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]'
FROM tenant t WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, process_key) DO NOTHING;

INSERT INTO approval_process (tenant_id, process_key, process_name, description, form_id, node_json)
SELECT id, 'overtime-approval', '加班单级审批', '直属上级单级审批',
       (SELECT f.id FROM approval_form f WHERE f.tenant_id = t.id AND f.form_key = 'overtime'),
       '[{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]'
FROM tenant t WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, process_key) DO NOTHING;

INSERT INTO approval_process (tenant_id, process_key, process_name, description, form_id, node_json)
SELECT id, 'purchase-approval', '采购单级审批', '直属上级单级审批，金额超限由规则追加财务节点',
       (SELECT f.id FROM approval_form f WHERE f.tenant_id = t.id AND f.form_key = 'purchase'),
       '[{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]'
FROM tenant t WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, process_key) DO NOTHING;

INSERT INTO approval_process (tenant_id, process_key, process_name, description, form_id, node_json)
SELECT id, 'payment-approval', '付款单级审批', '直属上级单级审批，大额付款由规则追加财务复核',
       (SELECT f.id FROM approval_form f WHERE f.tenant_id = t.id AND f.form_key = 'payment'),
       '[{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]'
FROM tenant t WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, process_key) DO NOTHING;