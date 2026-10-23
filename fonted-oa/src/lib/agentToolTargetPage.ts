import type { AgentTaskDetail, OaMenuItem } from '@/types/oa';
import { findMenu } from './navigationTree';

export type AgentRecordKind = 'dictionary-type' | 'dictionary-item';
export type AgentPageNavigator = (pageId: string, recordId?: number, recordKind?: AgentRecordKind) => boolean;

/** Only completed results select a page; arguments and output URLs never select records. */
export function agentTaskNavigationTarget(detail: Pick<AgentTaskDetail, 'status' | 'steps'>): { pageId: string; recordId?: number; recordKind?: AgentRecordKind } | null {
  if (detail.status !== 'SUCCEEDED' || !detail.steps.length
      || detail.steps.some((step) => step.status !== 'SUCCEEDED' || step.result == null)) return null;
  const last = detail.steps[detail.steps.length - 1];
  // The todo page cannot yet reproduce historical-status or date-filtered queries.
  if (last.toolCode === 'todo.query' && ((last.arguments?.status != null && last.arguments.status !== 'PENDING')
      || last.arguments?.from != null || last.arguments?.to != null)) return null;
  const recordKind = DICTIONARY_RECORD_KINDS.get(last.toolCode);
  return { pageId: agentToolTargetPage(last.toolCode), recordId: agentToolTargetRecordId(last.toolCode, last.result),
    ...(recordKind ? { recordKind } : {}) };
}

export function resolveAgentTaskTargetPath(taskId: string, detail: AgentTaskDetail, menus: OaMenuItem[]): string | null {
  if (detail.taskId !== taskId) return null;
  const target = agentTaskNavigationTarget(detail);
  return target ? resolveAgentToolTargetPath(target.pageId, target.recordId, menus, target.recordKind) : null;
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
  'approval.form.definition.query': 'form-engine',
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

const DICTIONARY_RECORD_KINDS: ReadonlyMap<string, AgentRecordKind> = new Map(
  (['type', 'item'] as const).flatMap((kind) => ['create', 'update', 'updateStatus']
    .map((operation): [string, AgentRecordKind] => [`dictionary.${kind}.${operation}`, `dictionary-${kind}`])),
);
const RECORD_ID_FIELDS: ReadonlyMap<string, string> = new Map([
  ...['approval.form.definition.query', 'approval.form.createDraft', 'approval.form.updateDraft', 'approval.form.publishDraft']
    .map((code): [string, string] => [code, 'formId']),
  ...['approval.process.createDraft', 'approval.process.updateDraft', 'approval.process.publishDraft']
    .map((code): [string, string] => [code, 'processId']),
  ...[...DICTIONARY_RECORD_KINDS].map(([code, kind]): [string, string] =>
    [code, kind === 'dictionary-type' ? 'dictionaryTypeId' : 'dictionaryItemId']),
  ...['integration.endpoint.createDraft', 'integration.endpoint.updateDraft', 'integration.endpoint.updateStatus']
    .map((code): [string, string] => [code, 'endpointId']),
  ['hr.employee.query', 'id'],
  ...['asset.create', 'asset.update', 'asset.claim', 'asset.return', 'asset.transfer',
    'asset.repair.start', 'asset.repair.complete', 'asset.inventory', 'asset.scrap'].map((code): [string, string] => [code, 'assetId']),
  ...['budget.createDraft', 'budget.updateDraft', 'budget.activateDraft', 'budget.cancelDraft']
    .map((code): [string, string] => [code, 'budgetId']),
  ...['supplier.createDraft', 'supplier.updateDraft', 'supplier.updateStatus']
    .map((code): [string, string] => [code, 'supplierId']),
  ...['expense.createDraft', 'expense.updateDraft', 'expense.submitDraft', 'expense.withdraw', 'expense.reopen', 'expense.cancelDraft', 'expense.remind']
    .map((code): [string, string] => [code, 'applicationId']),
  ...['contract.createDraft', 'contract.updateDraft', 'contract.updateStatus', 'contract.updateFulfillment',
    'contract.recordPayment', 'contract.remind'].map((code): [string, string] => [code, 'contractId']),
]);

/** Selection comes from the completed result, never model arguments or a supplied URL. */
export function agentToolTargetRecordId(toolCode: string, result: unknown): number | undefined {
  if (!result || typeof result !== 'object' || Array.isArray(result)) return undefined;
  const field = RECORD_ID_FIELDS.get(toolCode);
  if (!field) return undefined;
  const id = (result as Record<string, unknown>)[field];
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

/** Both navigation authorization and URL construction stay in code-owned boundaries. */
export function resolveAgentToolTargetPath(pageId: string, recordId: number | undefined, menus: OaMenuItem[], recordKind?: AgentRecordKind): string | null {
  const target = findMenu(pageId, menus);
  if (!target || target.type !== 'page' || !target.visible) return null;
  if (recordKind && pageId !== 'dictionary') return null;
  if (recordId !== undefined) {
    if (!Number.isSafeInteger(recordId) || recordId <= 0) return null;
    if (pageId === 'dictionary' && recordKind) return `/oa/dictionary?${recordKind === 'dictionary-type' ? 'typeId' : 'itemId'}=${recordId}`;
    if (pageId === 'employee-files') return `/oa/employee-files?id=${recordId}`;
    if (pageId === 'asset-ledger') return `/oa/asset-ledger?id=${recordId}`;
    if (pageId === 'budget') return `/oa/budget?id=${recordId}`;
    if (pageId === 'suppliers') return `/oa/suppliers?id=${recordId}`;
    if (pageId === 'expense') return `/oa/expense?id=${recordId}`;
    if (pageId === 'contracts') return `/oa/contracts?id=${recordId}`;
    if (pageId === 'api-center') return `/oa/api-center?id=${recordId}`;
    if (pageId === 'process-config') return `/oa/process-config?id=${recordId}`;
    if (pageId === 'form-engine') return `/oa/form-engine?id=${recordId}`;
    return null;
  }
  const path = target.path || `/oa/${target.id}`;
  return /^\/oa(?:\/[a-z][a-z0-9-]*)?$/.test(path) ? path : null;
}
