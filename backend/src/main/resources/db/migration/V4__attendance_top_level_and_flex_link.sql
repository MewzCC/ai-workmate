-- V4：考勤管理提为顶级菜单 ＋ 弹性联动下班配置
-- 1) 考勤管理由「业务系统 > 组织人事」下提升为顶级分组，与 工作台/业务系统/平台能力/系统设置 平级；
--    重新排序顶级分组：工作台=1, 业务系统=2, 考勤管理=3, 平台能力=4, 系统设置=5。
-- 2) attendance_setting 新增「弹性联动下班」开关：开启后预计下班时间随实际打卡时间顺延
--    （例：9:00 上班 → 18:00 下班；9:30 上班 → 18:30 下班）。
-- 采用显式赋值保证幂等，重复执行安全。

UPDATE rbac_route
SET parent_key = NULL,
    name       = '考勤管理',
    icon       = COALESCE(icon, 'attendance'),
    sort_order = 3,
    updated_at = CURRENT_TIMESTAMP
WHERE route_key = 'attendance';

UPDATE rbac_route SET sort_order = 4, updated_at = CURRENT_TIMESTAMP WHERE route_key = 'platform';
UPDATE rbac_route SET sort_order = 5, updated_at = CURRENT_TIMESTAMP WHERE route_key = 'settings';

ALTER TABLE attendance_setting
    ADD COLUMN IF NOT EXISTS flex_linked BOOLEAN NOT NULL DEFAULT FALSE;
