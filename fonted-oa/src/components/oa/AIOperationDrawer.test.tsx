import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import AIOperationDrawer from './AIOperationDrawer';

describe('AIOperationDrawer', () => {
  afterEach(cleanup);

  it('keeps the task composer in the fixed drawer footer', () => {
    render(
      <AIOperationDrawer
        open
        role="system_admin"
        pageId="access-control"
        pageTitle="角色、权限与动态路由"
        onClose={vi.fn()}
        onExecuted={vi.fn()}
      />,
    );

    const input = screen.getByPlaceholderText('例如：帮我预审当前列表，并输出风险排序');
    const footer = input.closest('.ant-drawer-footer');
    const submitButton = screen.getByRole('button', { name: /发送 \/ 生成计划/ });
    const cancelButton = screen.getByRole('button', { name: /取消计划/ });

    expect(footer).not.toBeNull();
    expect(footer?.contains(submitButton)).toBe(true);
    expect(footer?.contains(cancelButton)).toBe(true);
  });
});
