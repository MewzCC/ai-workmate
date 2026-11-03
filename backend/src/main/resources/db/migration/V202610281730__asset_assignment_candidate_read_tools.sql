-- Bounded asset-domain metadata reads. Existing write tools and release switches are unchanged.
INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT v.code,v.name,'AI 能力',v.description,t.id
FROM tenant t CROSS JOIN (VALUES
 ('agent:tool:asset.department.query','Agent 查询资产部门','按名称分页读取同租户有效部门编号与名称'),
 ('agent:tool:asset.custodian.query','Agent 查询资产保管候选人','按指定部门和姓名分页读取同租户有效保管候选人')
) AS v(code,name,description)
WHERE t.code='DEFAULT' ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT rp.tenant_id,rp.role_code,v.permission_code
FROM rbac_role_permission rp CROSS JOIN (VALUES
 ('agent:tool:asset.department.query'),('agent:tool:asset.custodian.query')
) AS v(permission_code)
WHERE rp.permission_code='asset:write'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
SELECT NULL,v.code,v.name,v.description,'1.0.0',v.input_schema::jsonb,
 '{"type":"object","additionalProperties":false,"required":["items","total","page","size"],"properties":{"items":{"type":"array","maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["id","name"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"departmentId":{"type":"integer","minimum":1}}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}'::jsonb,v.schema_hash,'L0','["asset:write"]'::jsonb,
 'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',20,16384,15000,'HASHED_ARGS_RESULT',TRUE
FROM (VALUES
 ('asset.department.query','Find asset assignment departments','Searches active departments in the authenticated tenant with asset write permission.',
  '{"type":"object","additionalProperties":false,"required":[],"properties":{"keyword":{"type":"string","maxLength":120},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}','sha256:a9ea3f82ecb378d78a566cb44f7f0eb4d51010fd9de1bf22752071de5f278e8f'),
 ('asset.custodian.query','Find asset custodians','Searches active users in one department of the authenticated tenant with asset write permission.',
  '{"type":"object","additionalProperties":false,"required":["departmentId"],"properties":{"departmentId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":120},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":20}}}','sha256:a8a7b1fef0eca2d1fb667377d269e8573cf888e0d4e907c8abaed7dfbe815841')
) AS v(code,name,description,input_schema,schema_hash)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

