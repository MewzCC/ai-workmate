CREATE TABLE IF NOT EXISTS budget_plan (
 id BIGSERIAL PRIMARY KEY, tenant_id BIGINT NOT NULL REFERENCES tenant(id), budget_code VARCHAR(64) NOT NULL,
 name VARCHAR(160) NOT NULL, fiscal_year INTEGER NOT NULL, owner_user_id BIGINT NOT NULL REFERENCES app_user(id),
 owner_label VARCHAR(160) NOT NULL, total_amount NUMERIC(18,2) NOT NULL, occupied_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
 spent_amount NUMERIC(18,2) NOT NULL DEFAULT 0, currency VARCHAR(8) NOT NULL DEFAULT 'CNY', warning_threshold INTEGER NOT NULL DEFAULT 80,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', summary VARCHAR(2000), version INTEGER NOT NULL DEFAULT 0,
 deleted BOOLEAN NOT NULL DEFAULT FALSE, created_by BIGINT NOT NULL REFERENCES app_user(id), updated_by BIGINT NOT NULL REFERENCES app_user(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_budget_amount CHECK (total_amount > 0 AND occupied_amount >= 0 AND spent_amount >= 0 AND occupied_amount + spent_amount <= total_amount),
 CONSTRAINT ck_budget_threshold CHECK (warning_threshold BETWEEN 1 AND 100),
 CONSTRAINT ck_budget_status CHECK (status IN ('DRAFT','ACTIVE','CLOSED','CANCELLED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_budget_plan_code ON budget_plan(tenant_id,budget_code) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_budget_plan_list ON budget_plan(tenant_id,fiscal_year,status,updated_at DESC) WHERE deleted=FALSE;

CREATE TABLE IF NOT EXISTS budget_transaction (
 id BIGSERIAL PRIMARY KEY, tenant_id BIGINT NOT NULL REFERENCES tenant(id), budget_id BIGINT NOT NULL REFERENCES budget_plan(id),
 transaction_type VARCHAR(24) NOT NULL, amount NUMERIC(18,2), occupied_before NUMERIC(18,2) NOT NULL,
 occupied_after NUMERIC(18,2) NOT NULL, spent_before NUMERIC(18,2) NOT NULL, spent_after NUMERIC(18,2) NOT NULL,
 reference_code VARCHAR(100), note VARCHAR(500), operator_id BIGINT NOT NULL REFERENCES app_user(id),
 operator_label VARCHAR(160) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_budget_transaction_timeline ON budget_transaction(tenant_id,budget_id,created_at DESC);

INSERT INTO budget_plan(tenant_id,budget_code,name,fiscal_year,owner_user_id,owner_label,total_amount,currency,
 warning_threshold,status,summary,version,deleted,created_by,updated_by,created_at,updated_at)
SELECT record.tenant_id,UPPER(record.record_code),record.title,EXTRACT(YEAR FROM record.created_at)::INTEGER,
 record.created_by,COALESCE(NULLIF(author.display_name,''),author.email,author.username),COALESCE(NULLIF(record.amount,0),1),'CNY',80,
 CASE WHEN record.status='ACTIVE' THEN 'ACTIVE' WHEN record.status='COMPLETED' THEN 'CLOSED' ELSE 'DRAFT' END,
 record.details,record.version,FALSE,record.created_by,record.updated_by,record.created_at,record.updated_at
FROM workbench_record record JOIN app_user author ON author.id=record.created_by AND author.tenant_id=record.tenant_id
WHERE record.module_key='budget' AND record.deleted=FALSE ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'budget:manage','管理预算','财务合同','编制预算并登记额度占用、释放与支出',tenant.id FROM tenant
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;
INSERT INTO rbac_role_permission(role_code,permission_code,tenant_id)
SELECT role.code,'budget:manage',role.tenant_id FROM rbac_role role WHERE role.code IN ('SUPER_ADMIN','FINANCE_ADMIN') ON CONFLICT DO NOTHING;
UPDATE rbac_route SET component_key='BUDGET',updated_at=CURRENT_TIMESTAMP WHERE route_key='budget';
