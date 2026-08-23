-- 流程审批完善：审批列表挂真实前端组件；停用暂无实现的占位页面
-- 1) approval-list 从 DASHBOARD（兜底空白页）切换到 APPROVAL_LIST 组件
UPDATE rbac_route
SET component_key = 'APPROVAL_LIST', updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'approval-list';

-- 2) form-engine / process-config / approval-rules 尚无后端实现与前端组件，
--    先停用避免进入空白占位页；权限点与路由数据保留，后续实现后可随时恢复
UPDATE rbac_route
SET enabled = FALSE, updated_at = CURRENT_TIMESTAMP
WHERE route_key IN ('form-engine', 'process-config', 'approval-rules');