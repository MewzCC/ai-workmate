import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AgentTaskDetail } from '@/types/oa';
import { bindAuthResponseIdentity } from '@/lib/authEvents';
import { useApplyPageUiTask } from './useApplyPageUiTask';

const mocks = vi.hoisted(() => ({ apply: vi.fn(), success: vi.fn(), error: vi.fn() }));
const bridge = { applyTask: mocks.apply };
vi.mock('@/components/oa/PageUiCommandBridge', () => ({ usePageUiCommandBridge: () => bridge }));
vi.mock('@/lib/antdMessage', () => ({ message: { success: mocks.success, error: mocks.error } }));
const detail = (status: AgentTaskDetail['status']) => ({ taskId: 'test-task', status } as AgentTaskDetail);

describe('live task page application', () => {
  beforeEach(() => { vi.clearAllMocks(); mocks.apply.mockResolvedValue(true); bindAuthResponseIdentity('user', 'allowed'); });
  it('keeps restored completed history read-only', () => {
    renderHook(() => useApplyPageUiTask(detail('SUCCEEDED')));
    expect(mocks.apply).not.toHaveBeenCalled();
  });
  it('applies a task observed running exactly once', async () => {
    const hook = renderHook(({ value }) => useApplyPageUiTask(value), { initialProps: { value: detail('RUNNING') } });
    hook.rerender({ value: detail('SUCCEEDED') });
    await waitFor(() => expect(mocks.success).toHaveBeenCalledTimes(1));
    hook.rerender({ value: detail('SUCCEEDED') });
    expect(mocks.apply).toHaveBeenCalledTimes(1);
  });
  it('does not apply another task or a partially succeeded task', () => {
    const hook = renderHook(({ value }) => useApplyPageUiTask(value), { initialProps: { value: detail('QUEUED') } });
    hook.rerender({ value: { ...detail('SUCCEEDED'), taskId: 'other-task' } });
    hook.rerender({ value: detail('PARTIALLY_SUCCEEDED') });
    expect(mocks.apply).not.toHaveBeenCalled();
  });
  it('rejects a terminal update after a permission change', () => {
    const hook = renderHook(({ value }) => useApplyPageUiTask(value), { initialProps: { value: detail('RUNNING') } });
    bindAuthResponseIdentity('user', 'revoked');
    hook.rerender({ value: detail('SUCCEEDED') });
    expect(mocks.apply).not.toHaveBeenCalled();
  });
  it('suppresses a delayed notification in a different identity', async () => {
    let finish!: (changed: boolean) => void;
    mocks.apply.mockReturnValue(new Promise<boolean>(resolve => { finish = resolve; }));
    const hook = renderHook(({ value }) => useApplyPageUiTask(value), { initialProps: { value: detail('RUNNING') } });
    hook.rerender({ value: detail('SUCCEEDED') });
    bindAuthResponseIdentity('other-user', 'allowed');
    await act(async () => { finish(true); });
    expect(mocks.success).not.toHaveBeenCalled();
    expect(mocks.error).not.toHaveBeenCalled();
  });
});
