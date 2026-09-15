import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { PageToolAvailabilityNotice } from './PageToolAvailabilityNotice';
import type { PageCapability } from '@/types/oa';
import i18n from '@/i18n';

const capability: PageCapability = {
  pageId: 'todo', componentKey: 'TODO_LIST', version: 1, uiCommands: [],
  dataScopePolicy: 'ASSIGNED_TO_SELF', effectiveDataScopes: ['SELF'], tools: [],
  unavailableReason: 'NO_AVAILABLE_TOOLS',
};

describe('PageToolAvailabilityNotice', () => {
  afterEach(async () => { cleanup(); await i18n.changeLanguage('zh-CN'); });

  it('explains an empty authorized tool set without internal policy details', () => {
    render(<PageToolAvailabilityNotice capability={capability} />);
    expect(screen.getByText(/当前账号在本页没有可用工具/)).toBeTruthy();
    expect(screen.getByText('暂无可执行动作')).toBeTruthy();
  });

  it('does not show unavailable feedback when an allowed tool exists', () => {
    render(<PageToolAvailabilityNotice capability={{ ...capability, unavailableReason: null,
      tools: [{ code: 'todo.query', name: 'Query', description: 'Query', riskLevel: 'L0',
        sideEffect: 'NONE', confirmationPolicy: 'NONE', ownershipPolicy: 'ASSIGNED_TO_SELF' }] }} />);
    expect(screen.queryByText('暂无可执行动作')).toBeNull();
  });

  it('renders the same safe reason in English', async () => {
    await i18n.changeLanguage('en-US');
    render(<PageToolAvailabilityNotice capability={capability} />);
    expect(screen.getByText(/No tools are available for your account on this page/)).toBeTruthy();
    expect(screen.queryByText(/当前账号/)).toBeNull();
  });

  it('does not reflect an unknown server reason into visible text', () => {
    render(<PageToolAvailabilityNotice capability={{ ...capability,
      unavailableReason: 'internal-secret' } as unknown as PageCapability} />);
    expect(screen.queryByText(/internal-secret/)).toBeNull();
  });
});
