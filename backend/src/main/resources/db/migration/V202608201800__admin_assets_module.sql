-- ============================================================
-- 行政资产模块：资产台账 / 会议室 / 访客预约 / 印章用印
-- ------------------------------------------------------------
-- 资产台账与会议室为简单 CRUD；访客预约与印章用印接入通用 workflow，
-- 与 leave_application 共享 workflow_instance / workflow_task / workflow_action_log。
-- ============================================================

-- ---------- 1. 业务表 ----------

CREATE TABLE IF NOT EXISTS asset_ledger (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    asset_code      VARCHAR(64) NOT NULL,
    name            VARCHAR(120) NOT NULL,
    category        VARCHAR(40) NOT NULL,
    specification   VARCHAR(120),
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_USE',
    department_id   BIGINT,
    owner_user_id   BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    purchase_date   DATE,
    original_value  NUMERIC(14, 2),
    remark          VARCHAR(500),
    deleted         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, asset_code),
    CONSTRAINT ck_asset_status CHECK (
        status IN ('IN_USE', 'IDLE', 'REPAIRING', 'SCRAPPED')
    )
);
CREATE INDEX IF NOT EXISTS idx_asset_tenant_status
    ON asset_ledger(tenant_id, status, deleted);
CREATE INDEX IF NOT EXISTS idx_asset_department
    ON asset_ledger(tenant_id, department_id);

CREATE TABLE IF NOT EXISTS meeting_room (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    code        VARCHAR(64) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    location    VARCHAR(200),
    capacity    INTEGER NOT NULL DEFAULT 0,
    facilities  VARCHAR(200),
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    remark      VARCHAR(500),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, code),
    CONSTRAINT ck_meeting_room_status CHECK (
        status IN ('OPEN', 'CLOSED')
    )
);
CREATE INDEX IF NOT EXISTS idx_meeting_room_tenant_status
    ON meeting_room(tenant_id, status, deleted);

CREATE TABLE IF NOT EXISTS visitor_booking (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    applicant_user_id    BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    approver_user_id     BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    workflow_instance_id BIGINT REFERENCES workflow_instance(id) ON DELETE RESTRICT,
    visitor_name         VARCHAR(60) NOT NULL,
    visitor_company      VARCHAR(120),
    visitor_phone        VARCHAR(40),
    purpose              VARCHAR(200) NOT NULL,
    host_user_id         BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    expected_visit_at    TIMESTAMP NOT NULL,
    expected_leave_at    TIMESTAMP,
    plate_number         VARCHAR(40),
    party_size           INTEGER NOT NULL DEFAULT 1,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version              INTEGER NOT NULL DEFAULT 0,
    submitted_at         TIMESTAMP,
    completed_at         TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_visitor_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'VISITED')
    )
);
CREATE INDEX IF NOT EXISTS idx_visitor_applicant_status
    ON visitor_booking(tenant_id, applicant_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_visitor_approver_status
    ON visitor_booking(tenant_id, approver_user_id, status, submitted_at DESC);

CREATE TABLE IF NOT EXISTS seal_usage (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    applicant_user_id    BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    approver_user_id     BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    workflow_instance_id BIGINT REFERENCES workflow_instance(id) ON DELETE RESTRICT,
    seal_type            VARCHAR(20) NOT NULL,
    document_title       VARCHAR(200) NOT NULL,
    usage_reason         VARCHAR(500) NOT NULL,
    copies               INTEGER NOT NULL DEFAULT 1,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    version              INTEGER NOT NULL DEFAULT 0,
    submitted_at         TIMESTAMP,
    completed_at         TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_seal_type CHECK (
        seal_type IN ('OFFICIAL', 'CONTRACT', 'LEGAL', 'FINANCE', 'OTHER')
    ),
    CONSTRAINT ck_seal_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')
    )
);
CREATE INDEX IF NOT EXISTS idx_seal_applicant_status
    ON seal_usage(tenant_id, applicant_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_seal_approver_status
    ON seal_usage(tenant_id, approver_user_id, status, submitted_at DESC);

-- ---------- 2. workflow_definition（访客预约 / 印章用印） ----------

INSERT INTO workflow_definition(tenant_id, code, name, business_type, version)
SELECT id, 'VISITOR_SINGLE_APPROVAL', '访客单级审批', 'VISITOR_BOOKING', 1
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, code, version) DO NOTHING;

INSERT INTO workflow_definition(tenant_id, code, name, business_type, version)
SELECT id, 'SEAL_SINGLE_APPROVAL', '印章单级审批', 'SEAL_USAGE', 1
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, code, version) DO NOTHING;

-- ---------- 3. 业务权限码 ----------

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT v.code, v.name, v.module, v.description, t.id
FROM tenant t
CROSS JOIN (VALUES
    ('asset:write',       '维护资产台账',     '行政资产', '新增/修改/删除资产台账'),
    ('meeting:write',     '维护会议室',       '行政资产', '新增/修改/删除会议室'),
    ('visitor:create',    '提交访客预约',     '行政资产', '提交访客来访预约申请'),
    ('visitor:read:self', '查看我的访客预约', '行政资产', '查看本人提交的访客预约'),
    ('visitor:withdraw',  '撤回访客预约',     '行政资产', '撤回自己审批中的访客预约'),
    ('seal:create',       '提交用印申请',     '行政资产', '提交印章使用申请'),
    ('seal:read:self',    '查看我的用印申请', '行政资产', '查看本人提交的用印申请'),
    ('seal:withdraw',     '撤回用印申请',     '行政资产', '撤回自己审批中的用印申请')
) AS v(code, name, module, description)
WHERE t.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

-- ---------- 4. 角色权限映射 ----------

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT v.role_code, v.permission_code, t.id
FROM tenant t
CROSS JOIN (VALUES
    ('SUPER_ADMIN',   'asset:write'),
    ('SYSTEM_ADMIN',  'asset:write'),
    ('SUPER_ADMIN',   'meeting:write'),
    ('SYSTEM_ADMIN',  'meeting:write'),
    ('SUPER_ADMIN',   'visitor:create'),
    ('SYSTEM_ADMIN',  'visitor:create'),
    ('PROCESS_ADMIN', 'visitor:create'),
    ('FINANCE_ADMIN', 'visitor:create'),
    ('EMPLOYEE',      'visitor:create'),
    ('SUPER_ADMIN',   'visitor:read:self'),
    ('SYSTEM_ADMIN',  'visitor:read:self'),
    ('PROCESS_ADMIN', 'visitor:read:self'),
    ('FINANCE_ADMIN', 'visitor:read:self'),
    ('EMPLOYEE',      'visitor:read:self'),
    ('SUPER_ADMIN',   'visitor:withdraw'),
    ('SYSTEM_ADMIN',  'visitor:withdraw'),
    ('PROCESS_ADMIN', 'visitor:withdraw'),
    ('FINANCE_ADMIN', 'visitor:withdraw'),
    ('EMPLOYEE',      'visitor:withdraw'),
    ('SUPER_ADMIN',   'seal:create'),
    ('SYSTEM_ADMIN',  'seal:create'),
    ('PROCESS_ADMIN', 'seal:create'),
    ('FINANCE_ADMIN', 'seal:create'),
    ('EMPLOYEE',      'seal:create'),
    ('SUPER_ADMIN',   'seal:read:self'),
    ('SYSTEM_ADMIN',  'seal:read:self'),
    ('PROCESS_ADMIN', 'seal:read:self'),
    ('FINANCE_ADMIN', 'seal:read:self'),
    ('EMPLOYEE',      'seal:read:self'),
    ('SUPER_ADMIN',   'seal:withdraw'),
    ('SYSTEM_ADMIN',  'seal:withdraw'),
    ('PROCESS_ADMIN', 'seal:withdraw'),
    ('FINANCE_ADMIN', 'seal:withdraw'),
    ('EMPLOYEE',      'seal:withdraw')
) AS v(role_code, permission_code)
WHERE t.code = 'DEFAULT'
ON CONFLICT DO NOTHING;

-- SUPER_ADMIN 默认持有所有新增权限
INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT 'SUPER_ADMIN', code, tenant_id
FROM rbac_permission
WHERE code IN ('asset:write', 'meeting:write',
              'visitor:create', 'visitor:read:self', 'visitor:withdraw',
              'seal:create', 'seal:read:self', 'seal:withdraw')
ON CONFLICT DO NOTHING;

-- ---------- 4.1 路由权限分配（4 个页面入口） ----------
-- asset-ledger / meeting-room 为管理员维护页；visitor-booking / seal-usage 全员可访问。
-- SUPER_ADMIN 由 UserAccessServiceImpl 自动获得全部权限，无需显式分配。

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT v.role_code, v.permission_code, t.id
FROM tenant t
CROSS JOIN (VALUES
    ('SYSTEM_ADMIN',  'route:asset-ledger'),
    ('SYSTEM_ADMIN',  'route:meeting-room'),
    ('SYSTEM_ADMIN',  'route:visitor-booking'),
    ('PROCESS_ADMIN', 'route:visitor-booking'),
    ('FINANCE_ADMIN', 'route:visitor-booking'),
    ('EMPLOYEE',      'route:visitor-booking'),
    ('SYSTEM_ADMIN',  'route:seal-usage'),
    ('PROCESS_ADMIN', 'route:seal-usage'),
    ('FINANCE_ADMIN', 'route:seal-usage'),
    ('EMPLOYEE',      'route:seal-usage')
) AS v(role_code, permission_code)
WHERE t.code = 'DEFAULT'
ON CONFLICT DO NOTHING;

-- ---------- 5. 更新 4 个路由的 component_key ----------

UPDATE rbac_route
SET component_key = 'ASSET_LEDGER', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'asset-ledger' AND component_key = 'DASHBOARD';

UPDATE rbac_route
SET component_key = 'MEETING_ROOM', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'meeting-room' AND component_key = 'DASHBOARD';

UPDATE rbac_route
SET component_key = 'VISITOR_BOOKING', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'visitor-booking' AND component_key = 'DASHBOARD';

UPDATE rbac_route
SET component_key = 'SEAL_USAGE', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'seal-usage' AND component_key = 'DASHBOARD';
