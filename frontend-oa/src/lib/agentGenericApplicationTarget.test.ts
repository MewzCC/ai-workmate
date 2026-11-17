import { describe, expect, it } from 'vitest';
import { agentTaskNavigationTarget, resolveAgentToolTargetPath } from './agentToolTargetPage';
import type { AgentTaskDetailStep, OaMenuItem } from '@/types/oa';

const menus: OaMenuItem[] = ['my-applications', 'approval-start'].map(id => ({ id, name: id, type: 'page', visible: true, sort: 1 }));
const step: AgentTaskDetailStep = { sequence: 1, toolCode: 'approval.application.apply', status: 'SUCCEEDED', riskLevel: 'L1', arguments: {},
  result: { applicationId: 17 }, resultSummary: 'Actual receipt', errorCode: null, startedAt: null, finishedAt: null };
describe('generic application result navigation', () => {
  it('selects the generic view using actual receipt IDs rather than model arguments', () => {
    const target = agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [{ ...step, arguments: { applicationId: 999 } }] })!;
    expect(target).toEqual({ pageId: 'my-applications', recordId: 17, recordKind: 'generic-application' });
    expect(resolveAgentToolTargetPath(target.pageId, target.recordId, menus, target.recordKind)).toBe('/oa/my-applications?kind=generic&id=17');
  });
  it('extracts a bounded detail projection without selecting its first field', () => {
    const target = agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [{ ...step, toolCode: 'approval.application.detail.query',
      result: { application: { applicationId: 23 }, fields: [{ id: 999 }] } }] });
    expect(target?.recordId).toBe(23);
  });
  it('rejects absent, hidden or directory entry routes and unsafe resource IDs', () => {
    expect(resolveAgentToolTargetPath('my-applications', 17, menus.slice(0, 1), 'generic-application')).toBeNull();
    for (const entry of [{ ...menus[1], visible: false }, { ...menus[1], type: 'group' as const }])
      expect(resolveAgentToolTargetPath('my-applications', 17, [menus[0], entry], 'generic-application')).toBeNull();
    expect(resolveAgentToolTargetPath('my-applications', Number.MAX_SAFE_INTEGER + 1, menus, 'generic-application')).toBeNull();
  });
});
