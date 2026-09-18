INSERT INTO rbac_permission(code,name,module,description,tenant_id)
SELECT 'attendance:reissue:apply','提交本人补卡申请','考勤管理',
       '仅允许提交本人补卡申请，不授予审批或直接修改打卡记录权限',id
FROM tenant WHERE code='DEFAULT'
ON CONFLICT (code) DO NOTHING;

INSERT INTO rbac_role_permission(tenant_id,role_code,permission_code)
SELECT DISTINCT tenant_id,role_code,'attendance:reissue:apply'
FROM rbac_role_permission WHERE permission_code='route:attendance-reissue'
ON CONFLICT DO NOTHING;
