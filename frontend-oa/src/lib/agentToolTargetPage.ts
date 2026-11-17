import type { AgentTaskDetail, OaMenuItem } from '@/types/oa';
import { findMenu } from './navigationTree';
import { requireTodoQuery, todoQueryPath, type TodoQueryFilters } from './todoQuery';

export type AgentRecordKind = 'dictionary-type' | 'dictionary-item' | 'generic-application' | 'generic-task';
export type AgentPageNavigator = (pageId: string, recordId?: number, recordKind?: AgentRecordKind, todoQuery?: TodoQueryFilters) => boolean;

/** Only completed results select a page; arguments and output URLs never select records. */
export function agentTaskNavigationTarget(detail: Pick<AgentTaskDetail, 'status' | 'steps'>): { pageId: string; recordId?: number; recordKind?: AgentRecordKind; todoQuery?: TodoQueryFilters } | null {
  if (detail.status !== 'SUCCEEDED' || !detail.steps.length
      || detail.steps.some((step) => step.status !== 'SUCCEEDED' || step.result == null)) return null;
  const last = detail.steps[detail.steps.length - 1];
  if (last.toolCode === 'page.ui.prepare') return null;
  if (last.toolCode === 'approval.genericTask.transfer') return { pageId: 'todo' };
  if (['approval.genericTask.detail.query', 'approval.genericTask.approve', 'approval.genericTask.reject', 'approval.genericTask.cc', 'approval.genericTask.addSign'].includes(last.toolCode)) {
    const recordId = agentToolTargetRecordId(last.toolCode, last.result);
    return recordId === undefined ? null : { pageId: 'todo', recordId, recordKind: 'generic-task' };
  }
  if (last.toolCode === 'todo.query' && last.result && typeof last.result === 'object' && !Array.isArray(last.result)
      && 'businessType' in last.result) {
    const result = last.result as Record<string, unknown>;
    if (!['businessType', 'status', 'page', 'size'].every(field => Object.prototype.hasOwnProperty.call(result, field))) return null;
    try { return { pageId: 'todo', todoQuery: requireTodoQuery(result) }; } catch { return null; }
  }
  // The todo page cannot yet reproduce historical-status or date-filtered queries.
  if (last.toolCode === 'todo.query' && ((last.arguments?.status != null && last.arguments.status !== 'PENDING')
      || last.arguments?.from != null || last.arguments?.to != null)) return null;
  const recordKind = last.toolCode.startsWith('approval.application.') && last.toolCode !== 'approval.application.template.query'
    ? 'generic-application' : DICTIONARY_RECORD_KINDS.get(last.toolCode);
  return { pageId: agentToolTargetPage(last.toolCode), recordId: agentToolTargetRecordId(last.toolCode, last.result),
    ...(recordKind ? { recordKind } : {}) };
}

export function resolveAgentTaskTargetPath(taskId: string, detail: AgentTaskDetail, menus: OaMenuItem[]): string | null {
  if (detail.taskId !== taskId) return null;
  const target = agentTaskNavigationTarget(detail);
  return target ? resolveAgentToolTargetPath(target.pageId, target.recordId, menus, target.recordKind, target.todoQuery) : null;
}

const EXACT_TARGETS: Record<string, string> = {
  'agentTask.mine.query': 'ai-tasks',
  'agentTask.cancel': 'ai-tasks',
  'todo.query': 'todo',
  'approval.genericTask.detail.query': 'todo',
  'approval.genericTask.approve': 'todo',
  'approval.genericTask.reject': 'todo',
  'approval.genericTask.participants.query': 'todo',
  'approval.genericTask.transfer': 'todo',
  'approval.genericTask.cc': 'todo',
  'approval.genericTask.addSign': 'todo',
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
  'approval.process.definition.query': 'process-config',
  'approval.application.template.query': 'approval-start',
  'approval.application.apply': 'my-applications',
  'expense.apply': 'expense',
  'approval.task.participants.query': 'approval-list',
  'approval.form.updateDraft': 'form-engine',
  'approval.form.patchDraftField': 'form-engine',
  'approval.form.publishDraft': 'form-engine',
  'approval.process.createDraft': 'process-config',
  'approval.process.updateDraft': 'process-config',
  'approval.process.publishDraft': 'process-config',
  'approval.rule.createDraft': 'approval-rules',
  'approval.rule.definition.query': 'approval-rules',
  'approval.rule.updateDraft': 'approval-rules',
  'approval.rule.patchDraftCondition': 'approval-rules',
  'approval.rule.enableDraft': 'approval-rules',
  'approval.task.query': 'approval-list',
  'hr.organization.query': 'org-tree',
  'hr.employee.query': 'employee-files',
  'hr.employee.documents.query': 'employee-files',
  'hr.change.query': 'employee-change',
  'hr.change.reviewer.query': 'employee-change',
  'visitor.host.query': 'visitor-booking',
  'visitor.approve': 'visitor-booking',
  'visitor.reject': 'visitor-booking',
  'seal.approve': 'seal-usage',
  'seal.documents.query': 'seal-usage',
  'seal.reject': 'seal-usage',
  'asset.department.query': 'asset-ledger',
  'asset.custodian.query': 'asset-ledger',
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
  ['seal.approve', 'resourceId'],
  ['seal.reject', 'resourceId'],
  ['visitor.approve', 'resourceId'],
  ['visitor.reject', 'resourceId'],
  ['approval.genericTask.detail.query', 'taskId'],
  ['approval.genericTask.approve', 'taskId'],
  ['approval.genericTask.reject', 'taskId'],
  ['approval.genericTask.cc', 'taskId'],
  ['approval.genericTask.addSign', 'taskId'],
  ...['approval.rule.definition.query', 'approval.rule.createDraft', 'approval.rule.updateDraft', 'approval.rule.patchDraftCondition', 'approval.rule.enableDraft']
    .map((code): [string, string] => [code, 'ruleId']),
  ...['approval.form.definition.query', 'approval.form.createDraft', 'approval.form.updateDraft', 'approval.form.patchDraftField', 'approval.form.publishDraft']
    .map((code): [string, string] => [code, 'formId']),
  ...['approval.process.definition.query', 'approval.process.createDraft', 'approval.process.updateDraft', 'approval.process.publishDraft']
    .map((code): [string, string] => [code, 'processId']),
  ...[...DICTIONARY_RECORD_KINDS].map(([code, kind]): [string, string] =>
    [code, kind === 'dictionary-type' ? 'dictionaryTypeId' : 'dictionaryItemId']),
  ...['integration.endpoint.createDraft', 'integration.endpoint.updateDraft', 'integration.endpoint.updateStatus']
    .map((code): [string, string] => [code, 'endpointId']),
  ['hr.employee.query', 'id'],
  ...['hr.change.apply', 'hr.change.approve', 'hr.change.reject', 'hr.change.withdraw']
    .map((code): [string, string] => [code, 'changeId']),
  ...['asset.create', 'asset.update', 'asset.claim', 'asset.return', 'asset.transfer',
    'asset.repair.start', 'asset.repair.complete', 'asset.inventory', 'asset.scrap'].map((code): [string, string] => [code, 'assetId']),
  ...['budget.createDraft', 'budget.updateDraft', 'budget.activateDraft', 'budget.cancelDraft']
    .map((code): [string, string] => [code, 'budgetId']),
  ...['supplier.createDraft', 'supplier.updateDraft', 'supplier.updateStatus']
    .map((code): [string, string] => [code, 'supplierId']),
  ...['approval.application.apply', 'approval.application.createDraft', 'approval.application.updateDraft', 'approval.application.submitDraft',
    'approval.application.cancelDraft', 'approval.application.withdraw', 'approval.application.reopen', 'approval.application.remind',
    'expense.apply', 'expense.createDraft', 'expense.updateDraft', 'expense.submitDraft', 'expense.withdraw', 'expense.reopen', 'expense.cancelDraft', 'expense.remind']
    .map((code): [string, string] => [code, 'applicationId']),
  ...['contract.createDraft', 'contract.updateDraft', 'contract.updateStatus', 'contract.updateFulfillment',
    'contract.recordPayment', 'contract.remind'].map((code): [string, string] => [code, 'contractId']),
]);

/** Selection comes from the completed result, never model arguments or a supplied URL. */
export function agentToolTargetRecordId(toolCode: string, result: unknown): number | undefined {
  if (!result || typeof result !== 'object' || Array.isArray(result)) return undefined;
  if (toolCode === 'approval.application.detail.query') {
    const application = (result as Record<string, unknown>).application;
    if (!application || typeof application !== 'object' || Array.isArray(application)) return undefined;
    const id = (application as Record<string, unknown>).applicationId;
    return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : undefined;
  }
  const field = RECORD_ID_FIELDS.get(toolCode);
  if (!field) return undefined;
  const id = (result as Record<string, unknown>)[field];
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

/** Both navigation authorization and URL construction stay in code-owned boundaries. */
export function resolveAgentToolTargetPath(pageId: string, recordId: number | undefined, menus: OaMenuItem[], recordKind?: AgentRecordKind, todoQuery?: TodoQueryFilters): string | null {
  const target = findMenu(pageId, menus);
  if (!target || target.type !== 'page' || !target.visible) return null;
  if (todoQuery !== undefined) {
    if (pageId !== 'todo' || recordId !== undefined || recordKind !== undefined) return null;
    try { return todoQueryPath(todoQuery); } catch { return null; }
  }
  if (recordKind === 'generic-application') {
    const entry = findMenu('approval-start', menus);
    if (pageId !== 'my-applications' || entry?.type !== 'page' || !entry.visible
        || recordId !== undefined && (!Number.isSafeInteger(recordId) || recordId <= 0)) return null;
    return `/oa/my-applications?kind=generic${recordId === undefined ? '' : `&id=${recordId}`}`;
  }
  if (recordKind === 'generic-task') {
    if (pageId !== 'todo' || recordId === undefined || !Number.isSafeInteger(recordId) || recordId <= 0) return null;
    return `/oa/todo?kind=generic&taskId=${recordId}`;
  }
  if (recordKind && pageId !== 'dictionary') return null;
  if (recordId !== undefined) {
    if (!Number.isSafeInteger(recordId) || recordId <= 0) return null;
    if (pageId === 'dictionary' && recordKind) return `/oa/dictionary?${recordKind === 'dictionary-type' ? 'typeId' : 'itemId'}=${recordId}`;
    if (pageId === 'employee-files') return `/oa/employee-files?id=${recordId}`;
    if (pageId === 'visitor-booking') return `/oa/visitor-booking?id=${recordId}`;
    if (pageId === 'seal-usage') return `/oa/seal-usage?id=${recordId}`;
    if (pageId === 'employee-change') return `/oa/employee-change?id=${recordId}`;
    if (pageId === 'asset-ledger') return `/oa/asset-ledger?id=${recordId}`;
    if (pageId === 'budget') return `/oa/budget?id=${recordId}`;
    if (pageId === 'suppliers') return `/oa/suppliers?id=${recordId}`;
    if (pageId === 'expense') return `/oa/expense?id=${recordId}`;
    if (pageId === 'contracts') return `/oa/contracts?id=${recordId}`;
    if (pageId === 'api-center') return `/oa/api-center?id=${recordId}`;
    if (pageId === 'process-config') return `/oa/process-config?id=${recordId}`;
    if (pageId === 'form-engine') return `/oa/form-engine?id=${recordId}`;
    if (pageId === 'approval-rules') return `/oa/approval-rules?id=${recordId}`;
    return null;
  }
  const path = target.path || `/oa/${target.id}`;
  return /^\/oa(?:\/[a-z][a-z0-9-]*)?$/.test(path) ? path : null;
}
