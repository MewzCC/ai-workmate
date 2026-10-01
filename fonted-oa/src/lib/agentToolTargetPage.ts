const EXACT_TARGETS: Record<string, string> = {
  'agentTask.mine.query': 'ai-tasks',
  'todo.query': 'todo',
  'knowledge.search': 'knowledge-base',
  'userPermission.mine.query': 'dashboard',
  'approval.configuration.query': 'process-config',
  'approval.task.query': 'approval-list',
  'hr.organization.query': 'org-tree',
  'hr.employee.query': 'employee-files',
  'hr.change.query': 'employee-change',
  'hr.change.apply': 'employee-change',
  'attendance.query': 'attendance-clock',
  'attendance.clock': 'attendance-clock',
  'attendance.reissue.apply': 'attendance-reissue',
  'attendance.reissue.decide': 'attendance-reissue',
  'attendance.settings.update': 'attendance-settings',
  'integration.endpoint.query': 'api-center',
  'pageAction.query': 'page-actions',
  'runtimeLog.query': 'runtime-logs',
  'sandboxReplay.query': 'sandbox-replay',
  'accessGovernance.query': 'access-control',
  'dataPermission.query': 'data-permission',
  'aiPermission.query': 'ai-permission',
  'audit.query': 'audit-center',
  'tenantConfiguration.query': 'tenant-config',
  'dictionary.query': 'dictionary',
  'systemCapability.query': 'system-config',
  'userSettings.update': 'system-config',
};

const PREFIX_TARGETS: Array<[string, string]> = [
  ['approval.task.', 'approval-list'],
  ['notification.', 'messages'],
  ['leave.', 'my-applications'],
  ['approval.application.', 'my-applications'],
  ['asset.', 'asset-ledger'],
  ['meeting.', 'meeting-room'],
  ['visitor.', 'visitor-booking'],
  ['seal.', 'seal-usage'],
  ['expense.', 'expense'],
  ['budget.', 'budget'],
  ['contract.', 'contracts'],
  ['supplier.', 'suppliers'],
];

/** Fixed navigation only. It never grants access; live route permissions remain authoritative. */
export function agentToolTargetPage(toolCode?: string | null): string {
  if (!toolCode) return 'ai-tasks';
  const exact = EXACT_TARGETS[toolCode];
  if (exact) return exact;
  return PREFIX_TARGETS.find(([prefix]) => toolCode.startsWith(prefix))?.[1] ?? 'ai-tasks';
}
