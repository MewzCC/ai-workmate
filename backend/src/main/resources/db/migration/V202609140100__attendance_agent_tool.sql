INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'attendance:read','读取考勤信息','考勤管理','允许按当前用户数据范围读取考勤信息',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:attendance.query','Agent 查询考勤','AI 能力',
       '允许 Agent 通过受控统一查询工具读取考勤页面安全摘要',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO UPDATE SET name=EXCLUDED.name,module=EXCLUDED.module,description=EXCLUDED.description;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'attendance:read'
FROM rbac_role_permission
WHERE permission_code IN ('route:attendance-clock','route:attendance-exception','route:attendance-reissue',
                          'route:attendance-statistics','route:attendance-settings')
ON CONFLICT DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:attendance.query'
FROM rbac_role_permission WHERE permission_code='attendance:read'
ON CONFLICT DO NOTHING;

INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'attendance.query','Query attendance views',
 'Returns one bounded attendance view selected from the authenticated page context.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["resource"],"properties":{"resource":{"type":"string","enum":["TODAY","RECORDS","EXCEPTIONS","MY_REISSUES","PENDING_REISSUES","STATISTICS","SETTINGS"]},"from":{"type":"string","format":"date"},"to":{"type":"string","format":"date"},"employeeId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"year":{"type":"integer","minimum":2000,"maximum":2100},"month":{"type":"integer","minimum":1,"maximum":12},"page":{"type":"integer","minimum":1,"maximum":10000},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 '{"type":"object","additionalProperties":false,"required":["resource","records","reissues","total","page","size"],"properties":{"resource":{"type":"string","enum":["TODAY","RECORDS","EXCEPTIONS","MY_REISSUES","PENDING_REISSUES","STATISTICS","SETTINGS"]},"today":{"type":"object","additionalProperties":false,"required":["clockDate","status","canClockIn","canClockOut"],"properties":{"id":{"type":"integer","minimum":1},"clockDate":{"type":"string","maxLength":10},"clockInTime":{"type":"string","maxLength":32},"clockOutTime":{"type":"string","maxLength":32},"status":{"type":"string","maxLength":40},"lateMinutes":{"type":"integer","minimum":0},"earlyLeaveMinutes":{"type":"integer","minimum":0},"canClockIn":{"type":"boolean"},"canClockOut":{"type":"boolean"}}},"records":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","employeeName","clockDate","status"],"properties":{"id":{"type":"integer","minimum":1},"employeeName":{"type":"string","maxLength":120},"clockDate":{"type":"string","maxLength":10},"clockInTime":{"type":"string","maxLength":32},"clockOutTime":{"type":"string","maxLength":32},"status":{"type":"string","maxLength":40},"lateMinutes":{"type":"integer","minimum":0},"earlyLeaveMinutes":{"type":"integer","minimum":0}}}},"reissues":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","applicantName","clockDate","clockType","reason","status","canDecide","canWithdraw"],"properties":{"id":{"type":"integer","minimum":1},"applicantName":{"type":"string","maxLength":120},"approverName":{"type":"string","maxLength":120},"clockDate":{"type":"string","maxLength":10},"clockType":{"type":"string","enum":["CLOCK_IN","CLOCK_OUT"]},"reason":{"type":"string","maxLength":1000},"status":{"type":"string","enum":["PENDING","APPROVED","REJECTED"]},"approverComment":{"type":"string","maxLength":1000},"submittedAt":{"type":"string","maxLength":32},"decidedAt":{"type":"string","maxLength":32},"canDecide":{"type":"boolean"},"canWithdraw":{"type":"boolean"}}}},"statistics":{"type":"object","additionalProperties":false,"required":["startDate","endDate","personal","team"],"properties":{"startDate":{"type":"string","maxLength":10},"endDate":{"type":"string","maxLength":10},"personal":{"type":"object","additionalProperties":false,"required":["employeeName","totalDays","normalDays","lateDays","earlyLeaveDays","missingDays","pendingReissueCount"],"properties":{"employeeName":{"type":"string","maxLength":120},"totalDays":{"type":"integer","minimum":0},"normalDays":{"type":"integer","minimum":0},"lateDays":{"type":"integer","minimum":0},"earlyLeaveDays":{"type":"integer","minimum":0},"missingDays":{"type":"integer","minimum":0},"pendingReissueCount":{"type":"integer","minimum":0}}},"team":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["employeeName","totalDays","normalDays","lateDays","earlyLeaveDays","missingDays"],"properties":{"employeeName":{"type":"string","maxLength":120},"departmentName":{"type":"string","maxLength":120},"totalDays":{"type":"integer","minimum":0},"normalDays":{"type":"integer","minimum":0},"lateDays":{"type":"integer","minimum":0},"earlyLeaveDays":{"type":"integer","minimum":0},"missingDays":{"type":"integer","minimum":0}}}}}},"settings":{"type":"object","additionalProperties":false,"required":["workStartTime","workEndTime","startFlexMinutes","endFlexMinutes","flexLinked"],"properties":{"workStartTime":{"type":"string","maxLength":16},"workEndTime":{"type":"string","maxLength":16},"startFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"endFlexMinutes":{"type":"integer","minimum":0,"maximum":480},"flexLinked":{"type":"boolean"},"updatedAt":{"type":"string","maxLength":32}}},"total":{"type":"integer","minimum":0},"page":{"type":"integer","minimum":1},"size":{"type":"integer","minimum":1,"maximum":50}}}'::jsonb,
 'sha256:4ff357d3836da753f0c32d3774ea04ba3cf83d1c6b8708de548e8ff711348d60',
 'L0','["attendance:read"]'::jsonb,'ALL','TENANT_SCOPED','READ_ONLY_SAFE','NONE','NONE',50,196608,15000,
 'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;
