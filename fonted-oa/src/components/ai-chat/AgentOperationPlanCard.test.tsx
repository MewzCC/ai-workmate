import { App } from 'antd';
import { cleanup, fireEvent, render, screen, within, waitFor } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import i18n from '@/i18n';
import type { AiTaskPlanResponse } from '@/types/oa';
import AgentOperationPlanCard from './AgentOperationPlanCard';

const plan: AiTaskPlanResponse = {
  taskId: 'confirmation-preview', planVersion: 1, planHash: 'bound-plan', status: 'WAITING_CONFIRMATION',
  riskLevel: 'L2', confirmationRequired: true, expiresAt: null, summary: 'One leave application',
  steps: [{ sequence: 1, toolCode: 'leave.apply', title: 'Apply leave', arguments: {
    startDate: '2026-11-01', endDate: '2026-11-02', reason: 'family matters', confirmationToken: 'never-visible',
  } }],
};

afterEach(async () => { cleanup(); await i18n.changeLanguage('zh-CN'); });

it.each(['zh-CN', 'en-US'])('reviews the exact bounded plan inside the %s confirmation dialog before a write', async (locale) => {
  await i18n.changeLanguage(locale);
  const confirm = vi.fn().mockResolvedValue(undefined);
  render(<App><AgentOperationPlanCard plan={plan} executing={false} onConfirm={confirm} onDiscard={vi.fn()} /></App>);
  fireEvent.click(screen.getByRole('button', { name: i18n.t('chat.operationConfirm') }));
  const dialog = await screen.findByRole('dialog', { name: i18n.t('chat.operationConfirmTitle') });
  expect(within(dialog).getByText('family matters')).toBeTruthy();
  expect(within(dialog).getByText('2026-11-01')).toBeTruthy();
  expect(within(dialog).getByText('2026-11-02')).toBeTruthy();
  expect(within(dialog).getByText('leave.apply')).toBeTruthy();
  expect(within(dialog).queryByText('never-visible')).toBeNull();
  expect(confirm).not.toHaveBeenCalled();
  fireEvent.click(within(dialog).getByRole('button', { name: (name) => name.replace(/\s/g, '') === i18n.t('common.cancel') }));
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  expect(confirm).not.toHaveBeenCalled();
});
