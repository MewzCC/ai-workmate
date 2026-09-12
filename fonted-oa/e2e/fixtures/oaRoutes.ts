export interface OaRouteFixture {
  routeKey: string;
  name: string;
  componentKey: string;
  parentKey: string;
}

export const enabledOaRoutes: readonly OaRouteFixture[] = [
  { routeKey: 'dashboard', name: '企业驾驶舱', componentKey: 'DASHBOARD', parentKey: 'workspace' },
  { routeKey: 'ai-workspace', name: 'AI 工作空间', componentKey: 'AI_WORKSPACE', parentKey: 'workspace' },
  { routeKey: 'ai-tasks', name: 'AI 任务中心', componentKey: 'AI_TASK_CENTER', parentKey: 'workspace' },
  { routeKey: 'todo', name: '我的待办', componentKey: 'TODO_LIST', parentKey: 'workspace' },
  { routeKey: 'messages', name: '消息中心', componentKey: 'MESSAGE_CENTER', parentKey: 'workspace' },
  { routeKey: 'leave-application', name: '请假申请', componentKey: 'LEAVE_FORM', parentKey: 'workspace' },
  { routeKey: 'my-applications', name: '我的申请', componentKey: 'MY_APPLICATIONS', parentKey: 'workspace' },

  { routeKey: 'approval-list', name: '审批中心', componentKey: 'APPROVAL_LIST', parentKey: 'approval' },
  { routeKey: 'approval-start', name: '发起审批', componentKey: 'APPROVAL_START', parentKey: 'approval' },
  { routeKey: 'approval-form', name: '申请', componentKey: 'APPROVAL_FORM', parentKey: 'approval' },
  { routeKey: 'form-engine', name: '表单管理', componentKey: 'FORM_ENGINE', parentKey: 'approval-manage' },
  { routeKey: 'process-config', name: '流程列表', componentKey: 'PROCESS_CONFIG', parentKey: 'approval-manage' },
  { routeKey: 'approval-rules', name: '审批规则', componentKey: 'APPROVAL_RULES', parentKey: 'approval-manage' },

  { routeKey: 'org-tree', name: '组织架构', componentKey: 'ORG_TREE', parentKey: 'hr' },
  { routeKey: 'employee-files', name: '员工档案', componentKey: 'EMPLOYEE_FILES', parentKey: 'hr' },
  { routeKey: 'employee-change', name: '入转调离', componentKey: 'EMPLOYEE_CHANGE', parentKey: 'hr' },

  { routeKey: 'asset-ledger', name: '资产台账', componentKey: 'ASSET_LEDGER', parentKey: 'assets' },
  { routeKey: 'meeting-room', name: '会议室', componentKey: 'MEETING_ROOM', parentKey: 'assets' },
  { routeKey: 'visitor-booking', name: '访客预约', componentKey: 'VISITOR_BOOKING', parentKey: 'assets' },
  { routeKey: 'seal-usage', name: '印章用印', componentKey: 'SEAL_USAGE', parentKey: 'assets' },

  { routeKey: 'expense', name: '费用报销', componentKey: 'EXPENSE', parentKey: 'finance' },
  { routeKey: 'budget', name: '预算中心', componentKey: 'BUDGET', parentKey: 'finance' },
  { routeKey: 'contracts', name: '合同管理', componentKey: 'CONTRACT', parentKey: 'finance' },
  { routeKey: 'suppliers', name: '供应商', componentKey: 'SUPPLIER', parentKey: 'finance' },

  { routeKey: 'api-center', name: '接口联调中心', componentKey: 'API_CENTER', parentKey: 'integration' },
  { routeKey: 'page-actions', name: '页面操作配置', componentKey: 'PAGE_ACTIONS', parentKey: 'integration' },
  { routeKey: 'runtime-logs', name: '运行日志', componentKey: 'RUNTIME_LOGS', parentKey: 'integration' },
  { routeKey: 'sandbox-replay', name: '沙箱回放', componentKey: 'SANDBOX_REPLAY', parentKey: 'integration' },

  { routeKey: 'attendance-clock', name: '打卡', componentKey: 'ATTENDANCE_CLOCK', parentKey: 'attendance' },
  { routeKey: 'attendance-exception', name: '异常考勤', componentKey: 'ATTENDANCE_EXCEPTION', parentKey: 'attendance' },
  { routeKey: 'attendance-reissue', name: '补卡申请', componentKey: 'ATTENDANCE_REISSUE', parentKey: 'attendance' },
  { routeKey: 'attendance-statistics', name: '考勤统计', componentKey: 'ATTENDANCE_STATISTICS', parentKey: 'attendance' },
  { routeKey: 'attendance-settings', name: '考勤设置', componentKey: 'ATTENDANCE_SETTINGS', parentKey: 'attendance' },

  { routeKey: 'access-control', name: '角色权限与路由', componentKey: 'ACCESS_CONTROL', parentKey: 'settings' },
  { routeKey: 'data-permission', name: '数据权限', componentKey: 'DATA_PERMISSION', parentKey: 'settings' },
  { routeKey: 'ai-permission', name: 'AI 操作权限', componentKey: 'AI_PERMISSION', parentKey: 'settings' },
  { routeKey: 'knowledge-base', name: '知识库管理', componentKey: 'KNOWLEDGE_BASE', parentKey: 'settings' },
  { routeKey: 'audit-center', name: '审计中心', componentKey: 'AUDIT_CENTER', parentKey: 'settings' },
  { routeKey: 'tenant-config', name: '租户配置', componentKey: 'TENANT_CONFIG', parentKey: 'settings' },
  { routeKey: 'dictionary', name: '数据字典', componentKey: 'DICTIONARY', parentKey: 'settings' },
  { routeKey: 'system-config', name: '系统配置', componentKey: 'SYSTEM_CONFIG', parentKey: 'settings' },
] as const;

const groupNames: Readonly<Record<string, string>> = {
  workspace: '工作台', business: '业务系统', approval: '流程审批', 'approval-manage': '流程管理',
  hr: '组织人事', assets: '行政资产', finance: '财务合同', platform: '平台能力',
  integration: '开放平台 / 联调', attendance: '考勤管理', settings: '系统设置',
};

const parentByGroup: Readonly<Record<string, string | undefined>> = {
  workspace: undefined, business: undefined, platform: undefined, attendance: undefined, settings: undefined,
  approval: 'business', 'approval-manage': 'approval', hr: 'business', assets: 'business', finance: 'business',
  integration: 'platform',
};

export function navigationFixture(routes: readonly OaRouteFixture[] = enabledOaRoutes) {
  const groups = Object.keys(groupNames).map((routeKey, index) => ({
    routeKey,
    parentKey: parentByGroup[routeKey],
    name: groupNames[routeKey],
    routeType: routeKey === 'approval-manage' || parentByGroup[routeKey] ? 'MENU' : 'GROUP',
    sortOrder: index + 1,
    children: [] as unknown[],
  }));
  const pages = routes.map((route, index) => ({
    routeKey: route.routeKey,
    parentKey: route.parentKey,
    name: route.name,
    path: `/oa/${route.routeKey}`,
    routeType: 'PAGE',
    componentKey: route.componentKey,
    permissionCode: `route:${route.routeKey}`,
    sortOrder: index + 1,
    children: [],
  }));
  const all = [...groups, ...pages];
  const childrenOf = (parentKey: string | undefined): unknown[] => all
    .filter((item) => item.parentKey === parentKey)
    .map((item) => ({ ...item, children: childrenOf(item.routeKey) }));
  return childrenOf(undefined);
}
