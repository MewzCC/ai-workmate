import { describe, expect, it } from 'vitest';
import type { AgentTaskDetailStep, OaMenuItem } from '@/types/oa';
import { agentTaskNavigationTarget, resolveAgentToolTargetPath } from './agentToolTargetPage';

describe('seal decision receipt navigation', () => {
  it.each(['seal.approve', 'seal.reject'])('uses the authorized usage ID returned by %s', toolCode => {
    const step: AgentTaskDetailStep = { sequence: 1, toolCode, status: 'SUCCEEDED', riskLevel: 'L2',
      arguments: { taskId: 20001, version: 2 }, result: { resourceId: 9, taskId: 20001 },
      resultSummary: '', errorCode: null, startedAt: null, finishedAt: null };
    const target = agentTaskNavigationTarget({ status: 'SUCCEEDED', steps: [step] });
    expect(target).toMatchObject({ pageId: 'seal-usage', recordId: 9 });
    const menu: OaMenuItem = { id: 'seal-usage', name: 'Seal usages', type: 'page', visible: true, sort: 1 };
    expect(resolveAgentToolTargetPath(target!.pageId, target!.recordId, [menu])).toBe('/oa/seal-usage?id=9');
    expect(resolveAgentToolTargetPath(target!.pageId, target!.recordId, [])).toBeNull();
    expect(agentTaskNavigationTarget({ status: 'FAILED', steps: [step] })).toBeNull();
  });
});
