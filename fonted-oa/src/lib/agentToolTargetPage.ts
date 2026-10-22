import type { AgentTaskDetail, OaMenuItem } from '@/types/oa';
import { findMenu } from './navigationTree';

export type AgentPageNavigator = (pageId: string, recordId?: number) => boolean;

/** Only completed results select a page; arguments and output URLs never select records. */
export function agentTaskNavigationTarget(detail: Pick<AgentTaskDetail, 'status' | 'steps'>): { pageId: string; recordId?: number } | null {
  if (detail.status !== 'SUCCEEDED' || !detail.steps.length
      || detail.steps.some((step) => step.status !== 'SUCCEEDED' || step.result == null)) return null;
  const last = detail.steps[detail.steps.length - 1];
  // The todo page cannot yet reproduce historical-status or date-filtered queries.
  if (last.toolCode === 'todo.query' && ((last.arguments?.status != null && last.arguments.status !== 'PENDING')
      || last.arguments?.from != null || last.arguments?.to != null)) return null;
  return { pageId: agentToolTargetPage(last.toolCode), recordId: agentToolTargetRecordId(last.toolCode, last.result) };
}

export function resolveAgentTaskTargetPath(taskId: string, detail: AgentTaskDetail, menus: OaMenuItem[]): string | null {
  if (detail.taskId !== taskId) return null;
  const target = agentTaskNavigationTarget(detail);
  return target ? resolveAgentToolTargetPath(target.pageId, target.recordId, menus) : null;
}

const EXACT_TARGETS: Record<string, string> = {
  'agentTask.mine.query': 'ai-tasks',
  'agentTask.cancel': 'ai-tasks',
  'todo.query': 'todo',
  'dashboard.overview.query': 'dashboard',
  'dashboard.preferences.update': 'dashboard',
  'knowledge.search': 'knowledge-base',
  'knowledge.base.query': 'knowledge-base',
  'knowledge.base.create': 'knowledge-base',
  'knowledge.base.update': 'knowledge-base',
  'knowledge.document.query': 'knowledge-base',
  'knowledge.document.reindex': 'knowledge-base',
  'knowledge.document.createText': 'knowledge-base',
  'userPermission.mine.query': 'dashboard',
  'approval.configuration.query': 'process-config',
  'approval.form.createDraft': 'form-engine',
  'approval.form.updateDraft': 'form-engine',
  'approval.form.publishDraft': 'form-engine',
  'approval.process.createDraft': 'process-config',
  'approval.process.updateDraft': 'process-config',
  'approval.process.publishDraft': 'process-config',
  'approval.rule.createDraft': 'approval-rules',
  'approval.rule.updateDraft': 'approval-rules',
  'approval.rule.enableDraft': 'approval-rules',
  'approval.task.query': 'approval-list',
  'hr.organization.query': 'org-tree',
  'hr.employee.query': 'employee-files',
  'hr.change.query': 'employee-change',
  'hr.change.apply': 'employee-change',
  'hr.change.approve': 'employee-change',
  'hr.change.reject': 'employee-change',
  'hr.change.withdraw': 'employee-change',
  'attendance.query': 'attendance-clock',
  'attendance.clock': 'attendance-clock',
  'attendance.reissue.apply': 'attendance-reissue',
  'attendance.reissue.decide': 'attendance-reissue',
  'attendance.settings.update': 'attendance-settings',
  'integration.endpoint.query': 'api-center',
  'integration.endpoint.createDraft': 'api-center',
  'integration.endpoint.updateDraft': 'api-center',
  'integration.endpoint.updateStatus': 'api-center',
  'pageAction.query': 'page-actions',
  'runtimeLog.query': 'runtime-logs',
  'observability.overview.query': 'platform-observability',
  'observability.preferences.update': 'platform-observability',
  'observability.thresholds.update': 'platform-observability',
  'sandboxReplay.query': 'sandbox-replay',
  'accessGovernance.query': 'access-control',
  'dataPermission.query': 'data-permission',
  'aiPermission.query': 'ai-permission',
  'audit.query': 'audit-center',
  'tenantConfiguration.query': 'tenant-config',
  'dictionary.query': 'dictionary',
  'dictionary.item.query': 'dictionary',
  'dictionary.type.create': 'dictionary',
  'dictionary.type.update': 'dictionary',
  'dictionary.item.create': 'dictionary',
  'dictionary.item.update': 'dictionary',
  'dictionary.type.updateStatus': 'dictionary',
  'dictionary.item.updateStatus': 'dictionary',
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

const ASSET_RECEIPT_TOOLS = new Set([
  'asset.create', 'asset.update', 'asset.claim', 'asset.return', 'asset.transfer',
  'asset.repair.start', 'asset.repair.complete', 'asset.inventory', 'asset.scrap',
]);
const BUDGET_RECEIPT_TOOLS = new Set(['budget.createDraft', 'budget.updateDraft', 'budget.activateDraft', 'budget.cancelDraft']);

/** Selection comes from the completed result, never model arguments or a supplied URL. */
export function agentToolTargetRecordId(toolCode: string, result: unknown): number | undefined {
  if (!result || typeof result !== 'object' || Array.isArray(result)) return undefined;
  const field = toolCode === 'hr.employee.query' ? 'id' : ASSET_RECEIPT_TOOLS.has(toolCode) ? 'assetId'
    : BUDGET_RECEIPT_TOOLS.has(toolCode) ? 'budgetId' : undefined;
  if (!field) return undefined;
  const id = (result as Record<string, unknown>)[field];
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

/** Both navigation authorization and URL construction stay in code-owned boundaries. */
export function resolveAgentToolTargetPath(pageId: string, recordId: number | undefined, menus: OaMenuItem[]): string | null {
  const target = findMenu(pageId, menus);
  if (!target || target.type !== 'page' || !target.visible) return null;
  if (recordId !== undefined) {
    if (!Number.isSafeInteger(recordId) || recordId <= 0) return null;
    if (pageId === 'employee-files') return `/oa/employee-files?id=${recordId}`;
    if (pageId === 'asset-ledger') return `/oa/asset-ledger?id=${recordId}`;
    if (pageId === 'budget') return `/oa/budget?id=${recordId}`;
    return null;
  }
  const path = target.path || `/oa/${target.id}`;
  return /^\/oa(?:\/[a-z][a-z0-9-]*)?$/.test(path) ? path : null;
}
