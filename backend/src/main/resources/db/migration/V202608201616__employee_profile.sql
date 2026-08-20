-- 员工档案页面改用独立前端组件，不再回退到 Dashboard 兜底页。
-- 已存在多次执行安全：仅当 component_key 尚未切换时才更新。
UPDATE rbac_route
SET component_key = 'EMPLOYEE_FILES'
WHERE route_key = 'employee-files'
  AND component_key <> 'EMPLOYEE_FILES';
