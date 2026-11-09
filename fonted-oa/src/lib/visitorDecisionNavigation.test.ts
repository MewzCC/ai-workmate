import { describe, expect, it } from 'vitest';
import type { AgentTaskDetailStep, OaMenuItem } from '@/types/oa';
import { agentTaskNavigationTarget, resolveAgentToolTargetPath } from './agentToolTargetPage';

describe('visitor decision receipt navigation', () => {
  it.each(['visitor.approve', 'visitor.reject'])('uses the authorized resource ID returned by %s', toolCode => {
    const step: AgentTaskDetailStep = { sequence: 1, toolCode, status: 'SUCCEEDED', riskLevel: 'L2',
      arguments: { taskId: 10001, version: 2 }, result: { resourceId: 9, taskId: 10001 },
      resultSummary: '', errorCode: null, startedAt: null, finishedAt: null };
    const target = agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [step] });
    expect(target).toMatchObject({ pageId: 'visitor-booking', recordId: 9 });
    const menu: OaMenuItem = { id: 'visitor-booking', name: 'Visitors', type: 'page', visible: true, sort: 1 };
    expect(resolveAgentToolTargetPath(target!.pageId, target!.recordId, [menu])).toBe('/oa/visitor-booking?id=9');
    expect(resolveAgentToolTargetPath(target!.pageId, target!.recordId, [])).toBeNull();
    expect(agentTaskNavigationTarget({ status: 'FAILED', steps: [step] })).toBeNull();
  });
});
