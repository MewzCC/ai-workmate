-- ============================================================
-- 通用申请页（审批表单）菜单接入
-- ------------------------------------------------------------
-- 新增「申请」页面路由（APPROVAL_FORM）：按 form_key 动态渲染
-- approval_form 表单并走通用提交接口 /api/approval-applications。
-- 权限复用 route:approval-start（与发起审批一致，EMPLOYEE 及以上可见，
-- 该权限已由 V202608221600 授予相关角色）。幂等。
-- ============================================================

INSERT INTO rbac_route(route_key, parent_key, name, path, icon, route_type, component_key, permission_code, sort_order)
VALUES ('approval-form', 'approval', '申请', '/oa/approval-form', NULL, 'PAGE', 'APPROVAL_FORM', 'route:approval-start', 3)
ON CONFLICT (route_key) DO UPDATE SET
    parent_key = EXCLUDED.parent_key, name = EXCLUDED.name, path = EXCLUDED.path,
    icon = EXCLUDED.icon, route_type = EXCLUDED.route_type, component_key = EXCLUDED.component_key,
    permission_code = EXCLUDED.permission_code, sort_order = EXCLUDED.sort_order,
    enabled = TRUE, updated_at = CURRENT_TIMESTAMP;

-- 流程管理目录顺延一位，避免排序并列
UPDATE rbac_route
SET sort_order = 4, updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'process-manage';
