import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import type { AgentTaskDetail, AgentTaskStatus } from '@/types/oa';
import i18n from '@/i18n';
import AgentOperationStatusCard from './AgentOperationStatusCard';
afterEach(cleanup);
function task(status: AgentTaskStatus): AgentTaskDetail {
  return { taskId: 'synthetic-status-task', pageId: 'ai-workspace', input: '', pageContext: {}, plan: {}, planHash: null,
    planVersion: 1, riskLevel: 'L0', status, steps: [], timeoutAt: null, createdAt: '', updatedAt: '', finishedAt: null, errorCode: null };
}
it.each<AgentTaskStatus>(['QUEUED', 'WAITING_CONFIRMATION'])(
  'allows cancellation of %s while observation continues', status => {
    const cancel = vi.fn().mockResolvedValue(undefined);
    render(<AgentOperationStatusCard task={task(status)} observing onRefresh={vi.fn()} onCancel={cancel} />);
    const button = screen.getByRole('button', { name: i18n.t('chat.operationDiscard') });
    expect(button).not.toBeDisabled(); fireEvent.click(button); expect(cancel).toHaveBeenCalledTimes(1);
  });
it.each<AgentTaskStatus>(['RUNNING', 'PARTIALLY_SUCCEEDED', 'SUCCEEDED'])(
  'does not offer cancellation or execution retry for %s', status => {
    render(<AgentOperationStatusCard task={task(status)} observing={false} onRefresh={vi.fn()} onCancel={vi.fn()} />);
    expect(screen.queryByRole('button', { name: i18n.t('chat.operationDiscard') })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: i18n.t('chat.regenerate') })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: i18n.t('chat.operationRefresh') })).toBeEnabled();
  });
