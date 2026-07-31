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

-- 启用 pgvector 扩展（docker-compose 已使用 pgvector/pgvector:pg16 镜像，插件已内置）
CREATE EXTENSION IF NOT EXISTS vector;

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
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS wallpaper VARCHAR(500);
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
    ('org-tree', 'hr', '组织架构', '/oa/org-tree', NULL, 'PAGE', 'ORG_TREE', 'route:org-tree', 1),
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

-- ============================================
-- Phase 1：默认租户、组织、多角色与请假审批
-- ============================================

CREATE TABLE IF NOT EXISTS tenant (
    id                 BIGSERIAL PRIMARY KEY,
    code               VARCHAR(40) NOT NULL UNIQUE,
    name               VARCHAR(120) NOT NULL,
    status             SMALLINT NOT NULL DEFAULT 1,
    permission_version BIGINT NOT NULL DEFAULT 1,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tenant(code, name)
VALUES ('DEFAULT', 'AI WorkMate')
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    updated_at = CURRENT_TIMESTAMP;

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS department_id BIGINT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS position_id BIGINT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS approver_user_id BIGINT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS permission_version BIGINT NOT NULL DEFAULT 1;

UPDATE app_user
SET tenant_id = (SELECT id FROM tenant WHERE code = 'DEFAULT')
WHERE tenant_id IS NULL;

ALTER TABLE conversation ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE attachment ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE knowledge_doc ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE access_audit_log ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE access_audit_log ADD COLUMN IF NOT EXISTS result VARCHAR(20) NOT NULL DEFAULT 'SUCCESS';
ALTER TABLE access_audit_log ADD COLUMN IF NOT EXISTS trace_id VARCHAR(64);
ALTER TABLE rbac_role ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE rbac_permission ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE rbac_role_permission ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE rbac_route ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE conversation c
SET tenant_id = u.tenant_id
FROM app_user u
WHERE c.user_id = u.id AND c.tenant_id IS NULL;

UPDATE attachment a
SET tenant_id = u.tenant_id
FROM app_user u
WHERE a.user_id = u.id AND a.tenant_id IS NULL;

UPDATE knowledge_doc k
SET tenant_id = u.tenant_id
FROM app_user u
WHERE k.user_id = u.id AND k.tenant_id IS NULL;

UPDATE access_audit_log a
SET tenant_id = u.tenant_id
FROM app_user u
WHERE a.operator_user_id = u.id AND a.tenant_id IS NULL;

UPDATE rbac_role
SET tenant_id = (SELECT id FROM tenant WHERE code = 'DEFAULT')
WHERE tenant_id IS NULL;
UPDATE rbac_permission
SET tenant_id = (SELECT id FROM tenant WHERE code = 'DEFAULT')
WHERE tenant_id IS NULL;
UPDATE rbac_role_permission
SET tenant_id = (SELECT id FROM tenant WHERE code = 'DEFAULT')
WHERE tenant_id IS NULL;
UPDATE rbac_route
SET tenant_id = (SELECT id FROM tenant WHERE code = 'DEFAULT')
WHERE tenant_id IS NULL;

ALTER TABLE app_user ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE conversation ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE attachment ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE knowledge_doc ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE access_audit_log ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE rbac_role ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE rbac_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE rbac_role_permission ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE rbac_route ALTER COLUMN tenant_id SET NOT NULL;

-- 兼容本文件前半段的历史 RBAC 种子语句：首次升级后再次执行时，
-- 未显式传 tenant_id 的种子数据仍必须归属 DEFAULT 租户。
DO $$
DECLARE
    default_tenant_id BIGINT;
BEGIN
    SELECT id INTO default_tenant_id FROM tenant WHERE code = 'DEFAULT';
    EXECUTE format(
        'ALTER TABLE rbac_role ALTER COLUMN tenant_id SET DEFAULT %s',
        default_tenant_id
    );
    EXECUTE format(
        'ALTER TABLE rbac_permission ALTER COLUMN tenant_id SET DEFAULT %s',
        default_tenant_id
    );
    EXECUTE format(
        'ALTER TABLE rbac_role_permission ALTER COLUMN tenant_id SET DEFAULT %s',
        default_tenant_id
    );
    EXECUTE format(
        'ALTER TABLE rbac_route ALTER COLUMN tenant_id SET DEFAULT %s',
        default_tenant_id
    );
END
$$;

CREATE INDEX IF NOT EXISTS idx_app_user_tenant_status
    ON app_user(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_conversation_tenant_user
    ON conversation(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_attachment_tenant_user
    ON attachment(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_doc_tenant_user
    ON knowledge_doc(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_rbac_role_tenant
    ON rbac_role(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_rbac_permission_tenant
    ON rbac_permission(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_rbac_route_tenant
    ON rbac_route(tenant_id, route_key);

CREATE TABLE IF NOT EXISTS department (
    id                       BIGSERIAL PRIMARY KEY,
    tenant_id                BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    parent_id                BIGINT REFERENCES department(id) ON DELETE RESTRICT,
    code                     VARCHAR(60) NOT NULL,
    name                     VARCHAR(100) NOT NULL,
    default_approver_user_id BIGINT,
    status                   SMALLINT NOT NULL DEFAULT 1,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, code)
);

CREATE INDEX IF NOT EXISTS idx_department_tenant_parent
    ON department(tenant_id, parent_id, status);

CREATE TABLE IF NOT EXISTS position (
    id         BIGSERIAL PRIMARY KEY,
    tenant_id  BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    code       VARCHAR(60) NOT NULL,
    name       VARCHAR(100) NOT NULL,
    status     SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, code)
);

CREATE TABLE IF NOT EXISTS user_role (
    tenant_id BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    user_id   BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role_code VARCHAR(40) NOT NULL REFERENCES rbac_role(code) ON DELETE RESTRICT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(tenant_id, user_id, role_code)
);

CREATE INDEX IF NOT EXISTS idx_user_role_tenant_role
    ON user_role(tenant_id, role_code, user_id);

CREATE TABLE IF NOT EXISTS data_scope (
    id          BIGSERIAL PRIMARY KEY,
    tenant_id   BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    role_code   VARCHAR(40) NOT NULL REFERENCES rbac_role(code) ON DELETE CASCADE,
    scope_type  VARCHAR(32) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, role_code),
    CONSTRAINT ck_data_scope_type CHECK (
        scope_type IN ('SELF', 'DEPARTMENT', 'DEPARTMENT_AND_CHILDREN', 'ALL')
    )
);

INSERT INTO department(tenant_id, code, name)
SELECT id, 'HEADQUARTERS', '总部'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, code) DO UPDATE SET
    name = EXCLUDED.name,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO position(tenant_id, code, name)
SELECT id, 'EMPLOYEE', '员工'
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, code) DO UPDATE SET
    name = EXCLUDED.name,
    updated_at = CURRENT_TIMESTAMP;

UPDATE app_user
SET department_id = (
        SELECT d.id FROM department d
        WHERE d.tenant_id = app_user.tenant_id AND d.code = 'HEADQUARTERS'
    ),
    position_id = (
        SELECT p.id FROM position p
        WHERE p.tenant_id = app_user.tenant_id AND p.code = 'EMPLOYEE'
    )
WHERE department_id IS NULL OR position_id IS NULL;

INSERT INTO user_role(tenant_id, user_id, role_code)
SELECT tenant_id, id, role
FROM app_user
ON CONFLICT DO NOTHING;

INSERT INTO data_scope(tenant_id, role_code, scope_type)
SELECT t.id, values.role_code, values.scope_type
FROM tenant t
CROSS JOIN (
    VALUES
        ('SUPER_ADMIN', 'ALL'),
        ('SYSTEM_ADMIN', 'ALL'),
        ('PROCESS_ADMIN', 'DEPARTMENT_AND_CHILDREN'),
        ('FINANCE_ADMIN', 'DEPARTMENT'),
        ('EMPLOYEE', 'SELF')
) AS values(role_code, scope_type)
WHERE t.code = 'DEFAULT'
ON CONFLICT (tenant_id, role_code) DO UPDATE SET
    scope_type = EXCLUDED.scope_type,
    updated_at = CURRENT_TIMESTAMP;

CREATE TABLE IF NOT EXISTS workflow_definition (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    code          VARCHAR(60) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    business_type VARCHAR(40) NOT NULL,
    version       INTEGER NOT NULL DEFAULT 1,
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, code, version)
);

CREATE TABLE IF NOT EXISTS workflow_instance (
    id             BIGSERIAL PRIMARY KEY,
    tenant_id      BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    definition_id  BIGINT NOT NULL REFERENCES workflow_definition(id) ON DELETE RESTRICT,
    business_type  VARCHAR(40) NOT NULL,
    business_id    BIGINT NOT NULL,
    applicant_id   BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    status         VARCHAR(20) NOT NULL,
    version        INTEGER NOT NULL DEFAULT 0,
    started_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at   TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, business_type, business_id),
    CONSTRAINT ck_workflow_instance_status CHECK (
        status IN ('RUNNING', 'COMPLETED', 'CANCELLED')
    )
);

CREATE TABLE IF NOT EXISTS workflow_task (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    instance_id          BIGINT NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    business_type        VARCHAR(40) NOT NULL,
    business_id          BIGINT NOT NULL,
    assignee_user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    status               VARCHAR(20) NOT NULL,
    decision_comment     VARCHAR(500),
    due_at               TIMESTAMP,
    version              INTEGER NOT NULL DEFAULT 0,
    completed_at         TIMESTAMP,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_workflow_task_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_workflow_task_active_business
    ON workflow_task(tenant_id, business_type, business_id)
    WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_workflow_task_assignee_status
    ON workflow_task(tenant_id, assignee_user_id, status, created_at DESC);

CREATE TABLE IF NOT EXISTS leave_application (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    applicant_user_id   BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    approver_user_id    BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    workflow_instance_id BIGINT REFERENCES workflow_instance(id) ON DELETE RESTRICT,
    leave_type          VARCHAR(24) NOT NULL,
    start_date          DATE NOT NULL,
    start_period        VARCHAR(2) NOT NULL,
    end_date            DATE NOT NULL,
    end_period          VARCHAR(2) NOT NULL,
    duration_half_days  INTEGER NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version             INTEGER NOT NULL DEFAULT 0,
    submitted_at        TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_leave_type CHECK (
        leave_type IN ('ANNUAL', 'PERSONAL', 'SICK', 'MARRIAGE', 'MATERNITY',
                       'PATERNITY', 'BEREAVEMENT', 'COMPENSATORY', 'OTHER')
    ),
    CONSTRAINT ck_leave_period CHECK (
        start_period IN ('AM', 'PM') AND end_period IN ('AM', 'PM')
    ),
    CONSTRAINT ck_leave_status CHECK (
        status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_leave_duration CHECK (duration_half_days > 0)
);

CREATE INDEX IF NOT EXISTS idx_leave_applicant_status
    ON leave_application(tenant_id, applicant_user_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_leave_approver_status
    ON leave_application(tenant_id, approver_user_id, status, submitted_at DESC);

CREATE TABLE IF NOT EXISTS workflow_action_log (
    id           BIGSERIAL PRIMARY KEY,
    tenant_id    BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    instance_id  BIGINT REFERENCES workflow_instance(id) ON DELETE CASCADE,
    task_id      BIGINT REFERENCES workflow_task(id) ON DELETE SET NULL,
    actor_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    action       VARCHAR(40) NOT NULL,
    from_status  VARCHAR(20),
    to_status    VARCHAR(20) NOT NULL,
    comment      VARCHAR(500),
    trace_id     VARCHAR(64) NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_action_instance
    ON workflow_action_log(tenant_id, instance_id, created_at, id);

CREATE TABLE IF NOT EXISTS business_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    tenant_id     BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    actor_user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    resource_type VARCHAR(40) NOT NULL,
    resource_id   VARCHAR(80) NOT NULL,
    action        VARCHAR(60) NOT NULL,
    result        VARCHAR(20) NOT NULL,
    summary       VARCHAR(500),
    trace_id      VARCHAR(64) NOT NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_business_audit_result CHECK (
        result IN ('SUCCESS', 'DENIED', 'CONFLICT', 'FAILURE')
    )
);

CREATE INDEX IF NOT EXISTS idx_business_audit_tenant_time
    ON business_audit_log(tenant_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_business_audit_actor_time
    ON business_audit_log(tenant_id, actor_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_business_audit_resource
    ON business_audit_log(tenant_id, resource_type, resource_id);

INSERT INTO workflow_definition(tenant_id, code, name, business_type, version)
SELECT id, 'LEAVE_SINGLE_APPROVAL', '请假单级审批', 'LEAVE_APPLICATION', 1
FROM tenant WHERE code = 'DEFAULT'
ON CONFLICT (tenant_id, code, version) DO UPDATE SET
    name = EXCLUDED.name,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_permission(code, name, module, description, tenant_id) VALUES
    ('leave:create', '创建请假申请', '请假审批', '创建、编辑并提交自己的请假申请',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('leave:read:self', '查看我的申请', '请假审批', '查看自己的请假申请与审批时间线',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('leave:withdraw', '撤回请假申请', '请假审批', '撤回自己审批中的请假申请',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('approval:act', '处理审批待办', '请假审批', '处理分配给自己的审批待办',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('route:leave-application', '访问请假申请', '页面访问', '允许访问请假申请页面',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('route:my-applications', '访问我的申请', '页面访问', '允许访问我的申请页面',
        (SELECT id FROM tenant WHERE code = 'DEFAULT'))
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

UPDATE rbac_route
SET component_key = 'TODO_LIST', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'todo';
UPDATE rbac_route
SET component_key = 'AUDIT_CENTER', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'audit-center';

INSERT INTO rbac_route(
    route_key, parent_key, name, path, icon, route_type, component_key,
    permission_code, sort_order, enabled, tenant_id
) VALUES
    ('leave-application', 'workspace', '请假申请', '/oa/leave-application', NULL,
        'PAGE', 'LEAVE_FORM', 'route:leave-application', 4, TRUE,
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('my-applications', 'workspace', '我的申请', '/oa/my-applications', NULL,
        'PAGE', 'MY_APPLICATIONS', 'route:my-applications', 5, TRUE,
        (SELECT id FROM tenant WHERE code = 'DEFAULT'))
ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key,
    name = EXCLUDED.name,
    path = EXCLUDED.path,
    component_key = EXCLUDED.component_key,
    permission_code = EXCLUDED.permission_code,
    sort_order = EXCLUDED.sort_order,
    enabled = TRUE,
    tenant_id = EXCLUDED.tenant_id,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT values.role_code, values.permission_code, t.id
FROM tenant t
CROSS JOIN (
    VALUES
        ('EMPLOYEE', 'leave:create'),
        ('EMPLOYEE', 'leave:read:self'),
        ('EMPLOYEE', 'leave:withdraw'),
        ('EMPLOYEE', 'route:leave-application'),
        ('EMPLOYEE', 'route:my-applications'),
        ('SYSTEM_ADMIN', 'leave:create'),
        ('SYSTEM_ADMIN', 'leave:read:self'),
        ('SYSTEM_ADMIN', 'leave:withdraw'),
        ('SYSTEM_ADMIN', 'approval:act'),
        ('SYSTEM_ADMIN', 'route:leave-application'),
        ('SYSTEM_ADMIN', 'route:my-applications'),
        ('PROCESS_ADMIN', 'leave:create'),
        ('PROCESS_ADMIN', 'leave:read:self'),
        ('PROCESS_ADMIN', 'leave:withdraw'),
        ('PROCESS_ADMIN', 'approval:act'),
        ('PROCESS_ADMIN', 'route:leave-application'),
        ('PROCESS_ADMIN', 'route:my-applications')
) AS values(role_code, permission_code)
WHERE t.code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT 'SUPER_ADMIN', code, tenant_id
FROM rbac_permission
ON CONFLICT DO NOTHING;

-- ============================================
-- Phase 1: 组织架构
-- ============================================
INSERT INTO rbac_permission(code, name, module, description, tenant_id) VALUES
    ('org:read', '查看组织架构', '组织人事', '查看部门、岗位、成员和汇报关系',
        (SELECT id FROM tenant WHERE code = 'DEFAULT')),
    ('org:manage', '管理组织架构', '组织人事', '维护部门、岗位、成员归属和审批关系',
        (SELECT id FROM tenant WHERE code = 'DEFAULT'))
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description,
    tenant_id = EXCLUDED.tenant_id;

UPDATE rbac_route
SET component_key = 'ORG_TREE',
    permission_code = 'route:org-tree',
    updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'org-tree';

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT role_code, permission_code, t.id
FROM tenant t
CROSS JOIN (
    VALUES
        ('EMPLOYEE', 'org:read'),
        ('EMPLOYEE', 'route:org-tree'),
        ('PROCESS_ADMIN', 'org:read'),
        ('PROCESS_ADMIN', 'route:org-tree'),
        ('FINANCE_ADMIN', 'org:read'),
        ('FINANCE_ADMIN', 'route:org-tree'),
        ('SYSTEM_ADMIN', 'org:read'),
        ('SYSTEM_ADMIN', 'org:manage'),
        ('SYSTEM_ADMIN', 'route:org-tree')
) AS defaults(role_code, permission_code)
WHERE t.code = 'DEFAULT'
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT 'SUPER_ADMIN', code, tenant_id
FROM rbac_permission
WHERE code IN ('org:read', 'org:manage', 'route:org-tree')
ON CONFLICT DO NOTHING;

COMMIT;
