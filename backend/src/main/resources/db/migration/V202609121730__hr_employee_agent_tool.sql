INSERT INTO rbac_permission(code, name, module, description, tenant_id)
SELECT 'agent:tool:hr.employee.query', 'Agent 查询员工档案', 'AI 能力',
       '允许 Agent 按当前用户实时数据范围只读查询单个员工安全档案', tenant.id
FROM tenant WHERE tenant.code = 'DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id, role_code, permission_code)
SELECT DISTINCT tenant_id, role_code, 'agent:tool:hr.employee.query'
FROM rbac_role_permission WHERE permission_code = 'hr:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool (
 tenant_id,code,name,description,handler_version,parameters_schema,output_schema,schema_hash,
 risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled
) VALUES (
 NULL,'hr.employee.query','Query visible employee profile',
 'Returns one employee profile only when visible in the authenticated actor''s live data scope.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["employeeId"],"properties":{"employeeId":{"type":"integer","minimum":1}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["id","name","role","status","employmentHistory","attendance","recentActivities"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"role":{"type":"string","maxLength":80},"status":{"type":"integer","minimum":0,"maximum":1},"createdAt":{"type":"string","maxLength":32},"departmentName":{"type":"string","maxLength":120},"positionName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"employmentHistory":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","changeType","effectiveDate"],"properties":{"id":{"type":"integer","minimum":1},"changeType":{"type":"string","maxLength":40},"effectiveDate":{"type":"string","maxLength":10},"targetDepartmentName":{"type":"string","maxLength":120},"targetPositionName":{"type":"string","maxLength":120},"targetSupervisorName":{"type":"string","maxLength":120},"appliedAt":{"type":"string","maxLength":32}}}},"attendance":{"type":"object","additionalProperties":false,"required":["totalDays","normalDays","lateDays","earlyLeaveDays","lateAndEarlyDays","missingClockDays"],"properties":{"totalDays":{"type":"integer","minimum":0},"normalDays":{"type":"integer","minimum":0},"lateDays":{"type":"integer","minimum":0},"earlyLeaveDays":{"type":"integer","minimum":0},"lateAndEarlyDays":{"type":"integer","minimum":0},"missingClockDays":{"type":"integer","minimum":0}}},"recentActivities":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","type","title","status","createdAt"],"properties":{"id":{"type":"integer","minimum":1},"type":{"type":"string","maxLength":40},"title":{"type":"string","maxLength":200},"status":{"type":"string","maxLength":40},"startDate":{"type":"string","maxLength":10},"endDate":{"type":"string","maxLength":10},"createdAt":{"type":"string","maxLength":32}}}}}}'::jsonb,
 'sha256:0df65eae1431949822629ffa8927d5f9077dfc87b0aea607495a845f0ea5918c',
 'L0','["hr:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,131072,15000,
 'HASHED_ARGS_RESULT',TRUE
)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
