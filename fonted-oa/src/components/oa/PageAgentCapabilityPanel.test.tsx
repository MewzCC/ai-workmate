import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import i18n from '@/i18n';
import type { PageCapability } from '@/types/oa';
import PageAgentCapabilityPanel from './PageAgentCapabilityPanel';

const capability: PageCapability = {
  pageId: 'todo',
  componentKey: 'TODO_LIST',
  version: 1,
  uiCommands: ['ui.navigate', 'ui.refreshPage'],
  dataScopePolicy: 'ASSIGNED_TO_SELF',
  effectiveDataScopes: ['SELF'],
  tools: [{
    code: 'todo.query',
    name: 'Query my approval tasks',
    description: 'Only my assigned approval tasks',
    riskLevel: 'L0',
    sideEffect: 'NONE',
    confirmationPolicy: 'NONE',
    ownershipPolicy: 'ASSIGNED_TO_SELF',
  }, {
    code: 'custom.read',
    name: 'Code-owned fallback tool',
    description: 'Fallback description',
    riskLevel: 'L0',
    sideEffect: 'NONE',
    confirmationPolicy: 'NONE',
    ownershipPolicy: 'TENANT_SCOPED',
  }],
  unavailableReason: null,
};

describe('PageAgentCapabilityPanel', () => {
  afterEach(async () => {
    cleanup();
    await i18n.changeLanguage('zh-CN');
  });

  it('renders server-filtered tools with shared risk metadata and generic commands', () => {
    const onSelectTool = vi.fn();
    render(
      <PageAgentCapabilityPanel
        pageTitle="我的待办"
        role="employee"
        capability={capability}
        loading={false}
        commandDisabled={false}
        onSelectTool={onSelectTool}
      />,
    );

    expect(screen.getByText('查询本人待办 · 只读 · L0')).toBeTruthy();
    expect(screen.getByText('Code-owned fallback tool · 只读 · L0')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: /使用 查询本人待办/ }));
    expect(onSelectTool).toHaveBeenCalledWith(
      '请使用“查询本人待办”处理当前页面任务；如果缺少必要信息，请先向我确认。',
    );
  });

  it('uses the same capability metadata in English without per-tool command duplication', async () => {
    await i18n.changeLanguage('en-US');
    const onSelectTool = vi.fn();
    render(
      <PageAgentCapabilityPanel
        pageTitle="My tasks"
        role="employee"
        capability={capability}
        loading={false}
        commandDisabled={false}
        onSelectTool={onSelectTool}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: /Use Query my tasks/ }));
    expect(onSelectTool).toHaveBeenCalledWith(
      'Use “Query my tasks” for the current page task. Ask me first if any required information is missing.',
    );
  });

  it('shows the safe unavailable state and no command when the filtered tool set is empty', () => {
    render(
      <PageAgentCapabilityPanel
        pageTitle="我的待办"
        role="employee"
        capability={{ ...capability, tools: [], unavailableReason: 'NO_AVAILABLE_TOOLS' }}
        loading={false}
        commandDisabled={false}
        onSelectTool={vi.fn()}
      />,
    );

    expect(screen.getByText('暂无可执行动作')).toBeTruthy();
    expect(screen.queryByText('快捷指令')).toBeNull();
  });
});
