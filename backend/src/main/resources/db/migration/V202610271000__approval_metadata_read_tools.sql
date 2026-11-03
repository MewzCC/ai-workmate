-- Read-only metadata tools. Existing write switches and tenant policies are unchanged.

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.process.definition.query','Agent 读取流程节点定义','AI 能力','Reads one authorized process as bounded semantic nodes and its current version.',id
FROM tenant WHERE code='DEFAULT' ON CONFLICT (code) DO NOTHING;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.process.definition.query'
FROM rbac_role_permission WHERE permission_code='approval:read' ON CONFLICT DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.process.definition.query','Read an approval process definition','Reads one authorized process as bounded semantic nodes and its current version.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["processId"],"properties":{"processId":{"type":"integer","minimum":1}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["processId","processKey","processName","status","version","nodes"],"properties":{"processId":{"type":"integer","minimum":1},"processKey":{"type":"string","maxLength":64},"processName":{"type":"string","maxLength":120},"formId":{"type":"integer","minimum":1},"status":{"type":"string","enum":["ENABLED","DISABLED"]},"version":{"type":"integer","minimum":0},"nodes":{"type":"array","minItems":3,"maxItems":20,"items":{"type":"object","additionalProperties":false,"required":["nodeType","nodeName"],"properties":{"nodeType":{"type":"string","enum":["START","APPROVAL","CONDITION","CC","DELAY","END"]},"nodeName":{"type":"string","maxLength":80},"approveType":{"type":"string","enum":["DIRECT_MANAGER","ROLE","DEPARTMENT","USER","SELF","MULTI_LEVEL"]},"targetKey":{"type":"string","maxLength":80},"mode":{"type":"string","enum":["COUNTERSIGN","OR_SIGN","SEQUENTIAL"]},"timeoutEnabled":{"type":"boolean"},"timeoutHours":{"type":"integer","minimum":1,"maximum":720},"timeoutAction":{"type":"string","enum":["REMIND","TRANSFER","AUTO_APPROVE"]}}}}}}'::jsonb,'sha256:20d35134bf16ef87614a46548e561146c32463e5ce16c740cde91cbac1437d29',
 'L0','["approval:read"]'::jsonb,'ALL','FIXED_RESOURCE','READ_ONLY_SAFE','NONE','NONE',
 20,65536,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'agent:tool:approval.task.participants.query','Agent 查询审批协作候选人','AI 能力','Lists up to 50 eligible collaboration candidates for one pending task assigned to the actor.',id
FROM tenant WHERE code='DEFAULT' ON CONFLICT (code) DO NOTHING;
INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'agent:tool:approval.task.participants.query'
FROM rbac_role_permission WHERE permission_code='approval:act' ON CONFLICT DO NOTHING;
INSERT INTO agent_tool(tenant_id,code,name,description,handler_version,parameters_schema,output_schema,
 schema_hash,risk_level,required_permissions,permission_mode,data_scope_policy,retry_policy,side_effect,
 confirmation_policy,max_result_items,max_result_bytes,timeout_ms,audit_level,enabled)
VALUES(NULL,'approval.task.participants.query','Query assigned approval participants','Lists up to 50 eligible collaboration candidates for one pending task assigned to the actor.','1.0.0',
 '{"type":"object","additionalProperties":false,"required":["taskId"],"properties":{"taskId":{"type":"integer","minimum":1},"keyword":{"type":"string","maxLength":64}}}'::jsonb,'{"type":"object","additionalProperties":false,"required":["items"],"properties":{"items":{"type":"array","maxItems":50,"items":{"type":"object","additionalProperties":false,"required":["id","name","canApprove"],"properties":{"id":{"type":"integer","minimum":1},"name":{"type":"string","maxLength":120},"canApprove":{"type":"boolean"}}}}}}'::jsonb,'sha256:c1f565d9ed17668eb3c885274fcebdafdead56a51e4b560f329f6668eeca2dc3',
 'L0','["approval:act"]'::jsonb,'ALL','ASSIGNED_TO_SELF','READ_ONLY_SAFE','NONE','NONE',
 50,32768,15000,'HASHED_ARGS_RESULT',TRUE)
ON CONFLICT (code) WHERE tenant_id IS NULL DO NOTHING;

