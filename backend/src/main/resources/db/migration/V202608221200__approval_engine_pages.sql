-- ============================================================
-- 流程审批配置中心：表单引擎 / 流程配置 / 审批规则
-- ------------------------------------------------------------
-- 1) 恢复三个页面路由，并挂接真实前端组件
--    （FORM_ENGINE / PROCESS_CONFIG / APPROVAL_RULES）；
-- 2) 新增审批引擎配置表：表单定义 / 流程定义 / 审批规则；
-- 3) 内置演示种子数据（幂等）。
-- JSON 字段采用 TEXT 存储：由应用层校验 JSON 合法性，本阶段不在 SQL 侧
-- 检索 JSON 内容，避免引入 PgJDBC JSONB 类型处理复杂度。
-- ============================================================

-- ---------- 1. 路由恢复 ----------
UPDATE rbac_route
SET component_key = 'FORM_ENGINE', enabled = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'form-engine';

UPDATE rbac_route
SET component_key = 'PROCESS_CONFIG', enabled = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'process-config';

UPDATE rbac_route
SET component_key = 'APPROVAL_RULES', enabled = TRUE, updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'approval-rules';

-- ---------- 2. 表单定义 ----------
CREATE TABLE IF NOT EXISTS approval_form (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    form_key    VARCHAR(64) NOT NULL,
    form_name   VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    schema_json TEXT NOT NULL DEFAULT '{}',
    status      VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    version     INTEGER NOT NULL DEFAULT 1,
    created_by  BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, form_key),
    CONSTRAINT ck_approval_form_status CHECK (status IN ('ENABLED', 'DISABLED'))
);
CREATE INDEX IF NOT EXISTS idx_approval_form_tenant_status
    ON approval_form(tenant_id, status, deleted);

-- ---------- 3. 流程定义 ----------
CREATE TABLE IF NOT EXISTS approval_process (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    process_key  VARCHAR(64) NOT NULL,
    process_name VARCHAR(120) NOT NULL,
    description  VARCHAR(500),
    form_id      BIGINT REFERENCES approval_form(id) ON DELETE SET NULL,
    node_json    TEXT NOT NULL DEFAULT '[]',
    status       VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    version      INTEGER NOT NULL DEFAULT 1,
    created_by   BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, process_key),
    CONSTRAINT ck_approval_process_status CHECK (status IN ('ENABLED', 'DISABLED'))
);
CREATE INDEX IF NOT EXISTS idx_approval_process_tenant_status
    ON approval_process(tenant_id, status, deleted);

-- ---------- 4. 审批规则 ----------
CREATE TABLE IF NOT EXISTS approval_rule (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    rule_key       VARCHAR(64) NOT NULL,
    rule_name      VARCHAR(120) NOT NULL,
    rule_type      VARCHAR(32) NOT NULL,
    priority       INTEGER NOT NULL DEFAULT 100,
    condition_json TEXT NOT NULL DEFAULT '{}',
    action_json    TEXT NOT NULL DEFAULT '{}',
    description    VARCHAR(500),
    status         VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    version        INTEGER NOT NULL DEFAULT 1,
    created_by     BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted        BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (tenant_id, rule_key),
    CONSTRAINT ck_approval_rule_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_approval_rule_type CHECK (
        rule_type IN ('AMOUNT_THRESHOLD', 'LEAVE_TYPE', 'EMPLOYEE_LEVEL', 'LIMIT_OVERRIDE')
    )
);
CREATE INDEX IF NOT EXISTS idx_approval_rule_tenant_status
    ON approval_rule(tenant_id, status, deleted);

-- ---------- 5. 种子数据（幂等） ----------

-- 表单定义：请假申请单 / 费用报销单
INSERT INTO approval_form (tenant_id, form_key, form_name, description, schema_json)
SELECT id, 'leave-application', '请假申请单', '请假审批场景的标准申请单模板',
       '{"fields":[{"name":"leaveType","label":"请假类型","type":"select"},{"name":"startDate","label":"开始时间","type":"date"},{"name":"endDate","label":"结束时间","type":"date"},{"name":"reason","label":"请假事由","type":"textarea"}]}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, form_key) DO NOTHING;

INSERT INTO approval_form (tenant_id, form_key, form_name, description, schema_json)
SELECT id, 'expense-application', '费用报销单', '差旅及日常费用报销申请单模板',
       '{"fields":[{"name":"amount","label":"报销金额","type":"number"},{"name":"category","label":"费用类型","type":"select"},{"name":"reason","label":"报销事由","type":"textarea"}]}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, form_key) DO NOTHING;

-- 流程定义：请假单级审批（关联请假申请单）
INSERT INTO approval_process (tenant_id, process_key, process_name, description, form_id, node_json)
SELECT id, 'leave-single-approval', '请假单级审批', '直属上级单级审批，超 3 天由规则追加部门负责人节点',
       (SELECT f.id FROM approval_form f WHERE f.tenant_id = t.id AND f.form_key = 'leave-application'),
       '[{"nodeName":"直属上级审批","approveType":"DIRECT_MANAGER","targetKey":""}]'
FROM tenant t WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, process_key) DO NOTHING;

-- 审批规则：请假超 3 天加签 / 报销超 5000 财务复核
INSERT INTO approval_rule (tenant_id, rule_key, rule_name, rule_type, priority, condition_json, action_json)
SELECT id, 'leave-over-3-days', '请假超 3 天加签部门负责人', 'LEAVE_TYPE', 10,
       '{"field":"durationDays","op":"gte","value":3}',
       '{"appendNode":"DEPARTMENT_HEAD","enabled":true}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, rule_key) DO NOTHING;

INSERT INTO approval_rule (tenant_id, rule_key, rule_name, rule_type, priority, condition_json, action_json)
SELECT id, 'expense-over-5000', '报销金额超 5000 需财务复核', 'AMOUNT_THRESHOLD', 20,
       '{"field":"amount","op":"gte","value":5000}',
       '{"appendNode":"FINANCE_REVIEW","enabled":true}'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, rule_key) DO NOTHING;