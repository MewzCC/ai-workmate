-- 单一数据库入口：新环境初始化与旧环境升级均执行本文件。
-- 所有结构和种子数据变更必须保持幂等，并置于同一事务中。
BEGIN;

-- 兼容早期使用 PostgreSQL 保留字 user 的用户表。
DO $$
BEGIN
    IF to_regclass('public.app_user') IS NULL
            AND to_regclass('public."user"') IS NOT NULL THEN
        ALTER TABLE "user" RENAME TO app_user;
    END IF;
END
$$;


-- ============================================
-- AI WorkMate 初始化 SQL
-- ============================================

-- 启用 pgvector 扩展（需要先安装 pgvector 插件）
-- CREATE EXTENSION IF NOT EXISTS vector;

-- 用户表
CREATE TABLE IF NOT EXISTS app_user (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(120) NOT NULL UNIQUE,
    display_name VARCHAR(50),
    password    VARCHAR(255) NOT NULL,
    email       VARCHAR(100),
    avatar      VARCHAR(500),
    role        VARCHAR(40)  NOT NULL DEFAULT 'EMPLOYEE',
    status      SMALLINT     NOT NULL DEFAULT 1,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_email ON app_user (LOWER(email)) WHERE email IS NOT NULL;

COMMENT ON TABLE app_user IS '用户表';
COMMENT ON COLUMN app_user.role IS 'RBAC role code';
COMMENT ON COLUMN app_user.status IS '1=正常 0=禁用';

-- 对话表
CREATE TABLE IF NOT EXISTS conversation (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(200) NOT NULL DEFAULT '新对话',
    model       VARCHAR(50)  NOT NULL DEFAULT 'deepseek-v4-flash',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_conv_user_id ON conversation(user_id);

-- 消息表
CREATE TABLE IF NOT EXISTS message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL,  -- user / assistant / system
    content         TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'success',
    feedback        VARCHAR(20),
    token_count     INT          DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_msg_conv_id ON message(conversation_id);

CREATE TABLE IF NOT EXISTS attachment (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    conversation_id BIGINT       NOT NULL,
    message_id      BIGINT,
    type            VARCHAR(20)  NOT NULL,
    name            VARCHAR(255) NOT NULL,
    storage_name    VARCHAR(100) NOT NULL UNIQUE,
    size            BIGINT       NOT NULL,
    mime_type       VARCHAR(150) NOT NULL,
    extracted_text  TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_attachment_user_conv ON attachment(user_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_attachment_message ON attachment(message_id);

-- 知识库文档表（第2月使用）
CREATE TABLE IF NOT EXISTS knowledge_doc (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    filename    VARCHAR(255) NOT NULL,
    file_size   BIGINT       NOT NULL,
    file_type   VARCHAR(20)  NOT NULL,
    chunk_count INT          DEFAULT 0,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 知识库向量块表（第2月使用，需要 pgvector）
-- CREATE TABLE IF NOT EXISTS knowledge_chunk (
--     id          BIGSERIAL PRIMARY KEY,
--     doc_id      BIGINT       NOT NULL,
--     chunk_index INT          NOT NULL,
--     content     TEXT         NOT NULL,
--     embedding   vector(1536),
--     created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
-- );


-- 兼容已存在的旧版表结构，可重复执行。
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS display_name VARCHAR(50);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS avatar VARCHAR(500);
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS role VARCHAR(40) NOT NULL DEFAULT 'EMPLOYEE';
ALTER TABLE app_user ALTER COLUMN username TYPE VARCHAR(120);
ALTER TABLE app_user ALTER COLUMN role TYPE VARCHAR(40);
CREATE UNIQUE INDEX IF NOT EXISTS ux_user_email
    ON app_user (LOWER(email))
    WHERE email IS NOT NULL;

ALTER TABLE message ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'success';
ALTER TABLE message ADD COLUMN IF NOT EXISTS feedback VARCHAR(20);

ALTER TABLE conversation
    ADD COLUMN IF NOT EXISTS model VARCHAR(50) NOT NULL DEFAULT 'deepseek-v4-flash';

ALTER TABLE conversation
    ALTER COLUMN model SET DEFAULT 'deepseek-v4-flash';

UPDATE conversation
SET model = 'deepseek-v4-flash'
WHERE model = 'deepseek-chat';


-- 角色与权限

CREATE TABLE IF NOT EXISTS rbac_role (
    code        VARCHAR(40) PRIMARY KEY,
    name        VARCHAR(60)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    builtin     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rbac_permission (
    code        VARCHAR(80) PRIMARY KEY,
    name        VARCHAR(80)  NOT NULL,
    module      VARCHAR(40)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rbac_role_permission (
    role_code       VARCHAR(40) NOT NULL REFERENCES rbac_role(code) ON DELETE CASCADE,
    permission_code VARCHAR(80) NOT NULL REFERENCES rbac_permission(code) ON DELETE CASCADE,
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_code, permission_code)
);

CREATE INDEX IF NOT EXISTS idx_rbac_role_permission_permission
    ON rbac_role_permission(permission_code);

CREATE TABLE IF NOT EXISTS access_audit_log (
    id               BIGSERIAL PRIMARY KEY,
    operator_user_id BIGINT       NOT NULL,
    action           VARCHAR(40)  NOT NULL,
    target_type      VARCHAR(40)  NOT NULL,
    target_id        VARCHAR(80)  NOT NULL,
    before_value     TEXT,
    after_value      TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_access_audit_operator_time
    ON access_audit_log(operator_user_id, created_at DESC);

INSERT INTO rbac_role (code, name, description) VALUES
    ('SUPER_ADMIN', '超级管理员', '拥有全部系统权限，权限集合不可修改'),
    ('SYSTEM_ADMIN', '系统管理员', '负责组织、平台配置和权限管理'),
    ('PROCESS_ADMIN', '流程管理员', '负责流程审批和运行管理'),
    ('FINANCE_ADMIN', '财务管理员', '负责财务合同及相关审批'),
    ('EMPLOYEE', '普通员工', '仅访问个人工作台和授权数据')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_permission (code, name, module, description) VALUES
    ('dashboard:read', '访问企业驾驶舱', '工作台', '查看企业驾驶舱'),
    ('ai-workspace:read', '访问 AI 工作空间', '工作台', '使用独立 AI 对话工作空间'),
    ('todo:read', '查看我的待办', '工作台', '查看个人待办事项'),
    ('messages:read', '查看消息中心', '工作台', '查看个人消息'),
    ('approval:read', '查看流程审批', '流程审批', '查看审批列表和流程配置'),
    ('approval:manage', '管理流程审批', '流程审批', '处理审批和配置流程'),
    ('hr:read', '查看组织人事', '组织人事', '查看组织和员工档案'),
    ('assets:read', '查看行政资产', '行政资产', '查看资产和行政资源'),
    ('finance:read', '查看财务合同', '财务合同', '查看财务和合同数据'),
    ('platform:read', '查看平台能力', '平台能力', '访问接口、日志和平台配置'),
    ('settings:read', '查看系统设置', '系统设置', '访问系统设置模块'),
    ('access:manage', '管理角色权限', '系统设置', '分配用户角色并配置角色权限'),
    ('audit:read', '查看审计记录', '系统设置', '查看系统审计数据'),
    ('data:export', '导出业务数据', '数据操作', '执行受控的数据导出'),
    ('ai:execute', '执行 AI 操作', 'AI 能力', '执行经过确认的 AI 页面操作')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission (role_code, permission_code)
SELECT 'SUPER_ADMIN', code FROM rbac_permission
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission (role_code, permission_code) VALUES
    ('SYSTEM_ADMIN', 'dashboard:read'),
    ('SYSTEM_ADMIN', 'ai-workspace:read'),
    ('SYSTEM_ADMIN', 'todo:read'),
    ('SYSTEM_ADMIN', 'messages:read'),
    ('SYSTEM_ADMIN', 'approval:read'),
    ('SYSTEM_ADMIN', 'approval:manage'),
    ('SYSTEM_ADMIN', 'hr:read'),
    ('SYSTEM_ADMIN', 'assets:read'),
    ('SYSTEM_ADMIN', 'platform:read'),
    ('SYSTEM_ADMIN', 'settings:read'),
    ('SYSTEM_ADMIN', 'access:manage'),
    ('SYSTEM_ADMIN', 'audit:read'),
    ('SYSTEM_ADMIN', 'ai:execute'),
    ('PROCESS_ADMIN', 'dashboard:read'),
    ('PROCESS_ADMIN', 'ai-workspace:read'),
    ('PROCESS_ADMIN', 'todo:read'),
    ('PROCESS_ADMIN', 'messages:read'),
    ('PROCESS_ADMIN', 'approval:read'),
    ('PROCESS_ADMIN', 'approval:manage'),
    ('PROCESS_ADMIN', 'platform:read'),
    ('PROCESS_ADMIN', 'ai:execute'),
    ('FINANCE_ADMIN', 'dashboard:read'),
    ('FINANCE_ADMIN', 'ai-workspace:read'),
    ('FINANCE_ADMIN', 'todo:read'),
    ('FINANCE_ADMIN', 'messages:read'),
    ('FINANCE_ADMIN', 'approval:read'),
    ('FINANCE_ADMIN', 'finance:read'),
    ('FINANCE_ADMIN', 'data:export'),
    ('EMPLOYEE', 'dashboard:read'),
    ('EMPLOYEE', 'ai-workspace:read'),
    ('EMPLOYEE', 'todo:read'),
    ('EMPLOYEE', 'messages:read')
ON CONFLICT DO NOTHING;

UPDATE app_user SET role = 'EMPLOYEE' WHERE role = 'USER';
UPDATE app_user SET role = 'SYSTEM_ADMIN' WHERE role = 'ADMIN';
UPDATE app_user
SET role = 'SUPER_ADMIN', updated_at = CURRENT_TIMESTAMP
WHERE id = (SELECT MIN(id) FROM app_user);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_app_user_rbac_role'
    ) THEN
        ALTER TABLE app_user
            ADD CONSTRAINT fk_app_user_rbac_role
            FOREIGN KEY (role) REFERENCES rbac_role(code);
    END IF;
END
$$;

-- 动态菜单与路由

CREATE TABLE IF NOT EXISTS rbac_route (
    route_key       VARCHAR(60) PRIMARY KEY,
    parent_key      VARCHAR(60),
    name            VARCHAR(80)  NOT NULL,
    path            VARCHAR(120),
    icon            VARCHAR(60),
    route_type      VARCHAR(12)  NOT NULL,
    component_key   VARCHAR(40),
    permission_code VARCHAR(80) REFERENCES rbac_permission(code) ON DELETE RESTRICT,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rbac_route_type CHECK (route_type IN ('GROUP', 'MENU', 'PAGE')),
    CONSTRAINT fk_rbac_route_parent FOREIGN KEY (parent_key)
        REFERENCES rbac_route(route_key) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_rbac_route_path
    ON rbac_route(path) WHERE path IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_rbac_route_parent_sort
    ON rbac_route(parent_key, sort_order);

INSERT INTO rbac_permission (code, name, module, description)
VALUES
    ('route:dashboard', '访问企业驾驶舱', '页面访问', '允许访问企业驾驶舱页面'),
    ('route:ai-workspace', '访问 AI 工作空间', '页面访问', '允许访问 AI 工作空间页面'),
    ('route:todo', '访问我的待办', '页面访问', '允许访问我的待办页面'),
    ('route:messages', '访问消息中心', '页面访问', '允许访问消息中心页面'),
    ('route:approval-list', '访问审批列表', '页面访问', '允许访问审批列表页面'),
    ('route:form-engine', '访问表单引擎', '页面访问', '允许访问表单引擎页面'),
    ('route:process-config', '访问流程配置', '页面访问', '允许访问流程配置页面'),
    ('route:approval-rules', '访问审批规则', '页面访问', '允许访问审批规则页面'),
    ('route:org-tree', '访问组织架构', '页面访问', '允许访问组织架构页面'),
    ('route:employee-files', '访问员工档案', '页面访问', '允许访问员工档案页面'),
    ('route:attendance', '访问考勤假勤', '页面访问', '允许访问考勤假勤页面'),
    ('route:employee-change', '访问入转调离', '页面访问', '允许访问入转调离页面'),
    ('route:asset-ledger', '访问资产台账', '页面访问', '允许访问资产台账页面'),
    ('route:meeting-room', '访问会议室', '页面访问', '允许访问会议室页面'),
    ('route:visitor-booking', '访问访客预约', '页面访问', '允许访问访客预约页面'),
    ('route:seal-usage', '访问印章用印', '页面访问', '允许访问印章用印页面'),
    ('route:expense', '访问费用报销', '页面访问', '允许访问费用报销页面'),
    ('route:budget', '访问预算中心', '页面访问', '允许访问预算中心页面'),
    ('route:contracts', '访问合同管理', '页面访问', '允许访问合同管理页面'),
    ('route:suppliers', '访问供应商', '页面访问', '允许访问供应商页面'),
    ('route:api-center', '访问接口联调中心', '页面访问', '允许访问接口联调中心页面'),
    ('route:page-actions', '访问页面操作配置', '页面访问', '允许访问页面操作配置页面'),
    ('route:runtime-logs', '访问运行日志', '页面访问', '允许运行日志页面'),
    ('route:sandbox-replay', '访问沙箱回放', '页面访问', '允许访问沙箱回放页面'),
    ('route:access-control', '访问权限配置', '页面访问', '允许配置用户、角色、权限和动态路由'),
    ('route:data-permission', '访问数据权限', '页面访问', '允许访问数据权限页面'),
    ('route:ai-permission', '访问 AI 操作权限', '页面访问', '允许访问 AI 操作权限页面'),
    ('route:audit-center', '访问审计中心', '页面访问', '允许访问审计中心页面'),
    ('route:tenant-config', '访问租户配置', '页面访问', '允许访问租户配置页面'),
    ('route:dictionary', '访问数据字典', '页面访问', '允许访问数据字典页面')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_route(route_key, parent_key, name, path, icon, route_type, component_key, permission_code, sort_order)
VALUES
    ('workspace', NULL, '工作台', NULL, 'DashboardOutlined', 'GROUP', NULL, NULL, 1),
    ('business', NULL, '业务系统', NULL, 'ApartmentOutlined', 'GROUP', NULL, NULL, 2),
    ('platform', NULL, '平台能力', NULL, 'ApiOutlined', 'GROUP', NULL, NULL, 3),
    ('settings', NULL, '系统设置', NULL, 'SettingOutlined', 'GROUP', NULL, NULL, 4)
ON CONFLICT (route_key) DO UPDATE SET
    name = EXCLUDED.name, icon = EXCLUDED.icon, sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_route(route_key, parent_key, name, path, icon, route_type, component_key, permission_code, sort_order)
VALUES
    ('approval', 'business', '流程审批', NULL, NULL, 'MENU', NULL, NULL, 1),
    ('hr', 'business', '组织人事', NULL, NULL, 'MENU', NULL, NULL, 2),
    ('assets', 'business', '行政资产', NULL, NULL, 'MENU', NULL, NULL, 3),
    ('finance', 'business', '财务合同', NULL, NULL, 'MENU', NULL, NULL, 4),
    ('integration', 'platform', '开放平台 / 联调', NULL, NULL, 'MENU', NULL, NULL, 1)
ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key, name = EXCLUDED.name, sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_route(route_key, parent_key, name, path, icon, route_type, component_key, permission_code, sort_order)
VALUES
    ('dashboard', 'workspace', '企业驾驶舱', '/oa/dashboard', NULL, 'PAGE', 'DASHBOARD', 'route:dashboard', 1),
    ('ai-workspace', 'workspace', 'AI 工作空间', '/oa/ai-workspace', 'RobotOutlined', 'PAGE', 'AI_WORKSPACE', 'route:ai-workspace', 2),
    ('todo', 'workspace', '我的待办', '/oa/todo', NULL, 'PAGE', 'DASHBOARD', 'route:todo', 3),
    ('messages', 'workspace', '消息中心', '/oa/messages', NULL, 'PAGE', 'DASHBOARD', 'route:messages', 4),
    ('approval-list', 'approval', '审批列表', '/oa/approval-list', NULL, 'PAGE', 'DASHBOARD', 'route:approval-list', 1),
    ('form-engine', 'approval', '表单引擎', '/oa/form-engine', NULL, 'PAGE', 'DASHBOARD', 'route:form-engine', 2),
    ('process-config', 'approval', '流程配置', '/oa/process-config', NULL, 'PAGE', 'DASHBOARD', 'route:process-config', 3),
    ('approval-rules', 'approval', '审批规则', '/oa/approval-rules', NULL, 'PAGE', 'DASHBOARD', 'route:approval-rules', 4),
    ('org-tree', 'hr', '组织架构', '/oa/org-tree', NULL, 'PAGE', 'DASHBOARD', 'route:org-tree', 1),
    ('employee-files', 'hr', '员工档案', '/oa/employee-files', NULL, 'PAGE', 'DASHBOARD', 'route:employee-files', 2),
    ('attendance', 'hr', '考勤假勤', '/oa/attendance', NULL, 'PAGE', 'DASHBOARD', 'route:attendance', 3),
    ('employee-change', 'hr', '入转调离', '/oa/employee-change', NULL, 'PAGE', 'DASHBOARD', 'route:employee-change', 4),
    ('asset-ledger', 'assets', '资产台账', '/oa/asset-ledger', NULL, 'PAGE', 'DASHBOARD', 'route:asset-ledger', 1),
    ('meeting-room', 'assets', '会议室', '/oa/meeting-room', NULL, 'PAGE', 'DASHBOARD', 'route:meeting-room', 2),
    ('visitor-booking', 'assets', '访客预约', '/oa/visitor-booking', NULL, 'PAGE', 'DASHBOARD', 'route:visitor-booking', 3),
    ('seal-usage', 'assets', '印章用印', '/oa/seal-usage', NULL, 'PAGE', 'DASHBOARD', 'route:seal-usage', 4),
    ('expense', 'finance', '费用报销', '/oa/expense', NULL, 'PAGE', 'DASHBOARD', 'route:expense', 1),
    ('budget', 'finance', '预算中心', '/oa/budget', NULL, 'PAGE', 'DASHBOARD', 'route:budget', 2),
    ('contracts', 'finance', '合同管理', '/oa/contracts', NULL, 'PAGE', 'DASHBOARD', 'route:contracts', 3),
    ('suppliers', 'finance', '供应商', '/oa/suppliers', NULL, 'PAGE', 'DASHBOARD', 'route:suppliers', 4),
    ('api-center', 'integration', '接口联调中心', '/oa/api-center', NULL, 'PAGE', 'DASHBOARD', 'route:api-center', 1),
    ('page-actions', 'integration', '页面操作配置', '/oa/page-actions', NULL, 'PAGE', 'DASHBOARD', 'route:page-actions', 2),
    ('runtime-logs', 'integration', '运行日志', '/oa/runtime-logs', NULL, 'PAGE', 'DASHBOARD', 'route:runtime-logs', 3),
    ('sandbox-replay', 'integration', '沙箱回放', '/oa/sandbox-replay', NULL, 'PAGE', 'DASHBOARD', 'route:sandbox-replay', 4),
    ('access-control', 'settings', '角色权限与路由', '/oa/access-control', NULL, 'PAGE', 'ACCESS_CONTROL', 'route:access-control', 1),
    ('data-permission', 'settings', '数据权限', '/oa/data-permission', NULL, 'PAGE', 'DASHBOARD', 'route:data-permission', 2),
    ('ai-permission', 'settings', 'AI 操作权限', '/oa/ai-permission', NULL, 'PAGE', 'DASHBOARD', 'route:ai-permission', 3),
    ('audit-center', 'settings', '审计中心', '/oa/audit-center', NULL, 'PAGE', 'DASHBOARD', 'route:audit-center', 4),
    ('tenant-config', 'settings', '租户配置', '/oa/tenant-config', NULL, 'PAGE', 'DASHBOARD', 'route:tenant-config', 5),
    ('dictionary', 'settings', '数据字典', '/oa/dictionary', NULL, 'PAGE', 'DASHBOARD', 'route:dictionary', 6)
ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key, name = EXCLUDED.name, path = EXCLUDED.path,
    icon = EXCLUDED.icon, route_type = EXCLUDED.route_type,
    component_key = EXCLUDED.component_key, permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order, updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_role_permission(role_code, permission_code)
SELECT 'SUPER_ADMIN', code FROM rbac_permission
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code)
SELECT role_code, 'route:' || route_key
FROM (
    VALUES
        ('EMPLOYEE', 'dashboard'), ('EMPLOYEE', 'ai-workspace'), ('EMPLOYEE', 'todo'), ('EMPLOYEE', 'messages'),
        ('SYSTEM_ADMIN', 'dashboard'), ('SYSTEM_ADMIN', 'ai-workspace'), ('SYSTEM_ADMIN', 'todo'), ('SYSTEM_ADMIN', 'messages'),
        ('SYSTEM_ADMIN', 'approval-list'), ('SYSTEM_ADMIN', 'form-engine'), ('SYSTEM_ADMIN', 'process-config'), ('SYSTEM_ADMIN', 'approval-rules'),
        ('SYSTEM_ADMIN', 'org-tree'), ('SYSTEM_ADMIN', 'employee-files'), ('SYSTEM_ADMIN', 'attendance'), ('SYSTEM_ADMIN', 'employee-change'),
        ('SYSTEM_ADMIN', 'asset-ledger'), ('SYSTEM_ADMIN', 'meeting-room'), ('SYSTEM_ADMIN', 'visitor-booking'), ('SYSTEM_ADMIN', 'seal-usage'),
        ('SYSTEM_ADMIN', 'api-center'), ('SYSTEM_ADMIN', 'page-actions'), ('SYSTEM_ADMIN', 'runtime-logs'), ('SYSTEM_ADMIN', 'sandbox-replay'),
        ('SYSTEM_ADMIN', 'access-control'), ('SYSTEM_ADMIN', 'data-permission'), ('SYSTEM_ADMIN', 'ai-permission'),
        ('SYSTEM_ADMIN', 'audit-center'), ('SYSTEM_ADMIN', 'dictionary'),
        ('PROCESS_ADMIN', 'dashboard'), ('PROCESS_ADMIN', 'ai-workspace'), ('PROCESS_ADMIN', 'todo'), ('PROCESS_ADMIN', 'messages'),
        ('PROCESS_ADMIN', 'approval-list'), ('PROCESS_ADMIN', 'form-engine'), ('PROCESS_ADMIN', 'process-config'),
        ('PROCESS_ADMIN', 'approval-rules'), ('PROCESS_ADMIN', 'page-actions'), ('PROCESS_ADMIN', 'runtime-logs'),
        ('FINANCE_ADMIN', 'dashboard'), ('FINANCE_ADMIN', 'ai-workspace'), ('FINANCE_ADMIN', 'todo'), ('FINANCE_ADMIN', 'messages'),
        ('FINANCE_ADMIN', 'approval-list'), ('FINANCE_ADMIN', 'expense'), ('FINANCE_ADMIN', 'budget'),
        ('FINANCE_ADMIN', 'contracts'), ('FINANCE_ADMIN', 'suppliers')
) AS defaults(role_code, route_key)
ON CONFLICT DO NOTHING;

COMMIT;
