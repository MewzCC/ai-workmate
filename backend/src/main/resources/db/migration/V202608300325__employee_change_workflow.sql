-- 入转调离业务：独立申请、审批与员工状态落地。
ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS employment_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS ck_app_user_employment_status;

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_employment_status CHECK (
        employment_status IN ('PRE_HIRE', 'PROBATION', 'ACTIVE', 'OFFBOARDED')
    );

CREATE TABLE IF NOT EXISTS employee_change (
    id                          BIGSERIAL PRIMARY KEY,
    tenant_id                   BIGINT NOT NULL REFERENCES tenant(id) ON DELETE RESTRICT,
    employee_user_id            BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    applicant_user_id           BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    review_approver_user_id      BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    change_type                 VARCHAR(20) NOT NULL,
    effective_date              DATE NOT NULL,
    current_department_id       BIGINT REFERENCES department(id) ON DELETE RESTRICT,
    current_position_id         BIGINT REFERENCES position(id) ON DELETE RESTRICT,
    current_supervisor_user_id  BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    target_department_id        BIGINT REFERENCES department(id) ON DELETE RESTRICT,
    target_position_id          BIGINT REFERENCES position(id) ON DELETE RESTRICT,
    target_supervisor_user_id   BIGINT REFERENCES app_user(id) ON DELETE RESTRICT,
    reason                      VARCHAR(1000) NOT NULL,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision_comment            VARCHAR(1000),
    version                     INTEGER NOT NULL DEFAULT 0,
    submitted_at                TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at                  TIMESTAMP,
    withdrawn_at                TIMESTAMP,
    applied_at                  TIMESTAMP,
    created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_employee_change_type CHECK (
        change_type IN ('ONBOARDING', 'REGULARIZATION', 'TRANSFER', 'OFFBOARDING')
    ),
    CONSTRAINT ck_employee_change_status CHECK (
        status IN ('PENDING', 'APPROVED', 'EFFECTIVE', 'REJECTED', 'WITHDRAWN')
    ),
    CONSTRAINT ck_employee_change_version CHECK (version >= 0)
);

CREATE INDEX IF NOT EXISTS idx_employee_change_tenant_status
    ON employee_change(tenant_id, status, submitted_at DESC);
CREATE INDEX IF NOT EXISTS idx_employee_change_employee
    ON employee_change(tenant_id, employee_user_id, submitted_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS ux_employee_change_pending_type
    ON employee_change(tenant_id, employee_user_id, change_type)
    WHERE status = 'PENDING';

INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'hr:manage', '管理员工变动', '组织人事', '创建、审批、撤回入转调离申请', t.id
FROM tenant t
ORDER BY t.id
LIMIT 1
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    module = EXCLUDED.module,
    description = EXCLUDED.description;

INSERT INTO rbac_role_permission(role_code, permission_code, tenant_id)
SELECT r.code, 'hr:manage', r.tenant_id
FROM rbac_role r
WHERE r.code IN ('SUPER_ADMIN', 'SYSTEM_ADMIN')
ON CONFLICT (role_code, permission_code) DO NOTHING;

UPDATE rbac_route
SET component_key = 'EMPLOYEE_CHANGE', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'employee-change';
