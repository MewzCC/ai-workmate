import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import i18n from '@/i18n';
import type { AiTaskPlanResponse } from '@/types/oa';
import AgentPlanPreview from './AgentPlanPreview';

const plan: AiTaskPlanResponse = {
  taskId: 'agt_preview',
  status: 'WAITING_CONFIRMATION',
  planVersion: 1,
  planHash: 'sha256:preview',
  riskLevel: 'L1',
  confirmationRequired: true,
  expiresAt: null,
  summary: '创建一条受控申请草稿',
  steps: [{
    sequence: 1,
    toolCode: 'leave.createDraft',
    title: 'Create leave draft',
    arguments: {
      leaveType: 'ANNUAL',
      reason: '家庭事务',
      confirmationToken: 'must-never-render',
    },
  }],
};

describe('AgentPlanPreview', () => {
  afterEach(async () => {
    cleanup();
    await i18n.changeLanguage('zh-CN');
  });

  it('shows the localized tool, bounded arguments and confirmation risk before execution', () => {
    render(<AgentPlanPreview plan={plan} />);
    expect(screen.getByText('创建请假草稿')).toBeTruthy();
    expect(screen.getByText('leave.createDraft')).toBeTruthy();
    expect(screen.getByText('家庭事务')).toBeTruthy();
    expect(screen.getByText('已隐藏敏感参数')).toBeTruthy();
    expect(screen.queryByText('must-never-render')).toBeNull();
    expect(screen.queryByText('confirmationToken')).toBeNull();
    expect(screen.getByText('需要确认')).toBeTruthy();
  });

  it('keeps the preview localized in English', async () => {
    await i18n.changeLanguage('en-US');
    render(<AgentPlanPreview plan={plan} />);
    expect(screen.getByText('Create leave draft')).toBeTruthy();
    expect(screen.getByText('Sensitive argument hidden')).toBeTruthy();
  });
});
