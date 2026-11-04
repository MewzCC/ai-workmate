import { describe, expect, it } from 'vitest';
import type { AgentTaskDetailStep, OaMenuItem } from '@/types/oa';
import { agentTaskNavigationTarget, resolveAgentToolTargetPath } from './agentToolTargetPage';

const menus: OaMenuItem[] = [{ id: 'todo', name: 'Tasks', type: 'page', visible: true, sort: 1 }];
const step: AgentTaskDetailStep = { sequence: 1, toolCode: 'approval.genericTask.detail.query', status: 'SUCCEEDED', riskLevel: 'L0',
  arguments: { taskId: 999 }, result: { taskId: 42, applicationId: 123, url: 'https://untrusted.invalid' },
  resultSummary: '', errorCode: null, startedAt: null, finishedAt: null };
describe('assigned generic task navigation', () => {
  it.each(['approval.genericTask.detail.query', 'approval.genericTask.approve'])('opens the task returned by %s using a fixed authorized route', toolCode => {
    const target = agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [{ ...step, toolCode }] });
    expect(target).toEqual({ pageId: 'todo', recordId: 42, recordKind: 'generic-task' });
    expect(resolveAgentToolTargetPath(target!.pageId, target!.recordId, menus, target!.recordKind)).toBe('/oa/todo?kind=generic&taskId=42');
  });
  it.each([undefined, 0, -1, '42', Number.MAX_SAFE_INTEGER + 1])('rejects invalid result task ID %s', taskId => {
    expect(agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [{ ...step, result: { taskId } }] })).toBeNull();
  });
  it('requires a successful task and currently visible todo menu', () => {
    expect(agentTaskNavigationTarget({ status: 'FAILED', steps: [step] })).toBeNull();
    expect(agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [{ ...step, status: 'FAILED' }] })).toBeNull();
    expect(resolveAgentToolTargetPath('todo', 42, [], 'generic-task')).toBeNull();
    expect(resolveAgentToolTargetPath('todo', 42, [{ ...menus[0], visible: false }], 'generic-task')).toBeNull();
    expect(resolveAgentToolTargetPath('dictionary', 42, [{ ...menus[0], id: 'dictionary' }], 'generic-task')).toBeNull();
    expect(resolveAgentToolTargetPath('todo', undefined, menus, 'generic-task')).toBeNull();
  });
});
