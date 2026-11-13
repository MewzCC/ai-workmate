import { expect, it } from 'vitest';
import { requireTodoQuery, todoQueryFromLocation, todoQueryPath } from './todoQuery';
import { agentTaskNavigationTarget, resolveAgentToolTargetPath } from './agentToolTargetPage';
import type { AgentTaskDetailStep, OaMenuItem } from '@/types/oa';

it('normalizes supported filters and preserves them in a controlled round-trip URL', () => {
  const query = requireTodoQuery({ businessType: 'GENERIC_APPROVAL', status: 'APPROVED', from: '2026-11-01', to: '2026-11-04T09:00', page: 3, size: 10 });
  const path = todoQueryPath(query); expect(path.startsWith('/oa/todo?')).toBe(true);
  expect(todoQueryFromLocation(new URL(path, 'http://localhost').searchParams)).toEqual(query);
  expect(query.from).toBe('2026-11-01T00:00:00'); expect(query.to).toBe('2026-11-04T09:00:00');
});
it.each([{ businessType: 'HR' }, { page: 10001 }, { size: 51 }, { page: '2' }, { status: 'WAITING' }, { from: '2026-02-30' }, { from: '2026-11-04T25:00:00' }, { from: '2026-11-04', to: '2026-11-01' }])('rejects invalid filter %j', value => {
  expect(() => requireTodoQuery(value)).toThrow();
});
it('rejects contradictory business tabs and invalid URL pages', () => {
  for (const value of ['kind=leave&businessType=GENERIC_APPROVAL', 'page=2.5', 'page=1e3', 'kind=unknown']) expect(() => todoQueryFromLocation(new URLSearchParams(value))).toThrow();
});
it('navigates from the actual completed query rather than model arguments or output URLs', () => {
  const query = requireTodoQuery({ businessType: 'GENERIC_APPROVAL', status: 'APPROVED', page: 2, size: 10, from: '2026-11-01' });
  const step: AgentTaskDetailStep = { sequence: 1, toolCode: 'todo.query', status: 'SUCCEEDED', riskLevel: 'L0',
    arguments: { businessType: 'LEAVE_APPLICATION' }, result: { ...query, total: 0, items: [], url: 'https://untrusted.invalid' }, resultSummary: '', errorCode: null, startedAt: null, finishedAt: null };
  const target = agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [step] }); expect(target?.todoQuery).toEqual(query);
  const menus: OaMenuItem[] = [{ id: 'todo', name: 'Tasks', type: 'page', visible: true, sort: 1 }];
  expect(resolveAgentToolTargetPath('todo', undefined, menus, undefined, target!.todoQuery)).toBe(todoQueryPath(query));
  expect(resolveAgentToolTargetPath('todo', undefined, [], undefined, query)).toBeNull();
  expect(resolveAgentToolTargetPath('todo', 42, menus, undefined, query)).toBeNull();
  expect(agentTaskNavigationTarget({ status: 'FAILED', steps: [step] })).toBeNull();
  expect(agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [{ ...step, result: { ...query, businessType: 'HR' } }] })).toBeNull();
});
