CREATE TABLE IF NOT EXISTS integration_endpoint (
 id BIGSERIAL PRIMARY KEY, tenant_id BIGINT NOT NULL REFERENCES tenant(id), endpoint_code VARCHAR(64) NOT NULL,
 name VARCHAR(160) NOT NULL, upstream_code VARCHAR(40) NOT NULL, http_method VARCHAR(10) NOT NULL,
 relative_path VARCHAR(500) NOT NULL, request_template VARCHAR(16000), description VARCHAR(2000),
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', version INTEGER NOT NULL DEFAULT 0, deleted BOOLEAN NOT NULL DEFAULT FALSE,
 created_by BIGINT NOT NULL REFERENCES app_user(id), updated_by BIGINT NOT NULL REFERENCES app_user(id),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_integration_endpoint_method CHECK (http_method IN ('GET','POST','PUT','PATCH','DELETE')),
 CONSTRAINT ck_integration_endpoint_status CHECK (status IN ('DRAFT','ACTIVE','DISABLED')),
 CONSTRAINT ck_integration_endpoint_relative_path CHECK (relative_path LIKE '/%' AND relative_path NOT LIKE '//%')
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_integration_endpoint_code ON integration_endpoint(tenant_id,endpoint_code) WHERE deleted=FALSE;
CREATE INDEX IF NOT EXISTS idx_integration_endpoint_list ON integration_endpoint(tenant_id,status,updated_at DESC) WHERE deleted=FALSE;

CREATE TABLE IF NOT EXISTS integration_invocation (
 id BIGSERIAL PRIMARY KEY, tenant_id BIGINT NOT NULL REFERENCES tenant(id), endpoint_id BIGINT NOT NULL REFERENCES integration_endpoint(id),
 request_hash VARCHAR(64) NOT NULL, outcome VARCHAR(16) NOT NULL, http_status INTEGER, duration_ms BIGINT NOT NULL,
 response_preview TEXT, error_code VARCHAR(60), operator_id BIGINT NOT NULL REFERENCES app_user(id),
 operator_label VARCHAR(160) NOT NULL, created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_integration_invocation_outcome CHECK (outcome IN ('SUCCESS','FAILED')),
 CONSTRAINT ck_integration_invocation_preview CHECK (response_preview IS NULL OR octet_length(response_preview) <= 65536)
);
CREATE INDEX IF NOT EXISTS idx_integration_invocation_timeline ON integration_invocation(tenant_id,endpoint_id,created_at DESC);

INSERT INTO integration_endpoint(tenant_id,endpoint_code,name,upstream_code,http_method,relative_path,description,status,
 version,deleted,created_by,updated_by,created_at,updated_at)
SELECT record.tenant_id,UPPER(record.record_code),record.title,'legacy-unconfigured','GET','/unconfigured',record.details,'DRAFT',
 record.version,FALSE,record.created_by,record.updated_by,record.created_at,record.updated_at
FROM workbench_record record WHERE record.module_key='api-center' AND record.deleted=FALSE ON CONFLICT DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT permission.code,permission.name,permission.module,permission.description,tenant.id FROM tenant CROSS JOIN (VALUES
 ('integration:endpoint:manage','管理受控接口','开放平台','维护已登记沙箱接口与请求模板'),
 ('integration:endpoint:execute','执行接口联调','开放平台','调用服务端配置的受控沙箱上游并记录结果')
) AS permission(code,name,module,description) ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;
INSERT INTO rbac_role_permission(role_code,permission_code,tenant_id)
SELECT role.code,permission.code,role.tenant_id FROM rbac_role role CROSS JOIN (VALUES('integration:endpoint:manage'),('integration:endpoint:execute')) AS permission(code)
WHERE role.code IN ('SUPER_ADMIN','SYSTEM_ADMIN') ON CONFLICT DO NOTHING;
UPDATE rbac_route SET component_key='API_CENTER',updated_at=CURRENT_TIMESTAMP WHERE route_key='api-center';
