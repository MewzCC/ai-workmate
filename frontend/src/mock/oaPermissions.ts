import { oaMenus } from './oaMenus';
import type { AIAction, OaMenuItem, OaRole, PermissionAction } from '@/types/oa';

export const roleOptions: Array<{ label: string; value: OaRole }> = [
  { label: '超级管理员', value: 'super_admin' },
  { label: '系统管理员', value: 'system_admin' },
  { label: '流程管理员', value: 'process_admin' },
  { label: '财务管理员', value: 'finance_admin' },
  { label: '普通员工', value: 'employee' },
];

export const roleDataScope: Record<OaRole, string> = {
  super_admin: '全租户、全业务域',
  system_admin: '本租户系统配置与审计数据',
  process_admin: '流程审批、页面操作与运行日志',
  finance_admin: '财务合同域、审批列表只读',
  employee: '本人待办、本人消息与授权数据',
};

export const aiActions: AIAction[] = [
  { actionId: 'preview-approval', name: '预审当前列表', pageId: 'dashboard', type: 'approve', riskLevel: 'high', requiredPermission: 'approve', requireConfirm: true, executeApi: '/api/ai/tasks/execute' },
  { actionId: 'create-purchase', name: '新建采购申请', pageId: 'dashboard', type: 'create', riskLevel: 'medium', requiredPermission: 'create', requireConfirm: true, executeApi: '/api/ai/tasks/execute' },
  { actionId: 'update-employee', name: '修改员工部门', pageId: 'dashboard', type: 'update', riskLevel: 'high', requiredPermission: 'update', requireConfirm: true, executeApi: '/api/ai/tasks/execute' },
  { actionId: 'debug-api', name: '排查接口异常', pageId: 'dashboard', type: 'debug', riskLevel: 'medium', requiredPermission: 'read', requireConfirm: false, executeApi: '/api/ai/tasks/execute' },
  { actionId: 'export-summary', name: '导出审批摘要', pageId: 'dashboard', type: 'export', riskLevel: 'high', requiredPermission: 'export', requireConfirm: true, executeApi: '/api/ai/tasks/execute' },
];

function roleCanSee(item: OaMenuItem, role: OaRole): boolean {
  if (!item.visible) return false;
  if (role === 'super_admin') return true;
  return !item.roles || item.roles.includes(role);
}

export function filterMenusByRole(role: OaRole, menus: OaMenuItem[] = oaMenus): OaMenuItem[] {
  return menus
    .filter((item) => roleCanSee(item, role))
    .map((item) => ({
      ...item,
      children: item.children ? filterMenusByRole(role, item.children) : undefined,
    }))
    .filter((item) => !item.children || item.children.length > 0);
}

export function findMenu(menuId: string, menus: OaMenuItem[] = oaMenus): OaMenuItem | undefined {
  for (const menu of menus) {
    if (menu.id === menuId) return menu;
    const child = menu.children ? findMenu(menuId, menu.children) : undefined;
    if (child) return child;
  }
  return undefined;
}

export function can(role: OaRole, menuId: string, action: PermissionAction): boolean {
  if (role === 'super_admin') return true;
  const menu = findMenu(menuId);
  if (!menu || !roleCanSee(menu, role)) return false;
  return menu.actions?.includes(action) ?? action === 'read';
}

export function getAllowedAiActions(role: OaRole, pageId: string): AIAction[] {
  return aiActions.filter((action) => {
    if (role === 'super_admin') return action.pageId === pageId;
    if (role === 'employee' && ['approve', 'delete', 'export'].includes(action.type)) {
      return false;
    }
    return action.pageId === pageId && can(role, pageId, action.requiredPermission);
  });
}

export function isSensitiveEmployeeTask(role: OaRole, input: string): boolean {
  if (role !== 'employee') return false;
  return ['批量审批', '导出敏感数据', '修改权限', '删除数据'].some((keyword) => input.includes(keyword));
}
