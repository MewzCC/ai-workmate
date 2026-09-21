import { createRef, type RefObject } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { OaRole } from '@/types/oa';
import PageAgentLauncher, { type PageAgentLauncherHandle } from './PageAgentLauncher';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('./AiMiniPanel', () => ({
  default: ({ onOpenAi }: { onOpenAi: (prompt?: string) => void }) => (
    <button type="button" onClick={() => onOpenAi('mini-prompt')}>
      mini-agent
    </button>
  ),
}));

vi.mock('./AIOperationDrawer', () => ({
  default: ({
    open,
    pageId,
    pageTitle,
    initialPrompt,
    onClose,
    onOpenChangeComplete,
    onExecutionCompleted,
  }: {
    open: boolean;
    pageId: string;
    pageTitle: string;
    initialPrompt?: string;
    onClose: () => void;
    onOpenChangeComplete?: (present: boolean) => void;
    onExecutionCompleted?: () => void;
  }) => (
    <section
      data-testid="agent-drawer"
      data-open={String(open)}
      data-page-id={pageId}
      data-page-title={pageTitle}
      data-prompt={initialPrompt || ''}
    >
      <button type="button" onClick={onClose}>close-agent</button>
      <button type="button" onClick={() => onOpenChangeComplete?.(true)}>drawer-present</button>
      <button type="button" onClick={onExecutionCompleted}>execution-completed</button>
    </section>
  ),
}));

interface HarnessProps {
  pageId: string;
  pageTitle: string;
  miniEnabled?: boolean;
  role?: OaRole;
  launcherRef: RefObject<PageAgentLauncherHandle | null>;
  onPageRefresh?: () => void;
}

function Harness({
  pageId,
  pageTitle,
  miniEnabled = true,
  role = 'system_admin',
  launcherRef,
  onPageRefresh,
}: HarnessProps) {
  return (
    <>
      <button type="button" onClick={() => launcherRef.current?.open('page-prompt')}>
        page-agent
      </button>
      <PageAgentLauncher
        ref={launcherRef}
        role={role}
        pageId={pageId}
        pageTitle={pageTitle}
        miniEnabled={miniEnabled}
        onPageRefresh={onPageRefresh}
      />
    </>
  );
}

describe('PageAgentLauncher', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('provides one shared page-aware entry for both page actions and the mini panel', () => {
    const launcherRef = createRef<PageAgentLauncherHandle>();
    render(
      <Harness
        launcherRef={launcherRef}
        pageId="meeting-room"
        pageTitle="会议室"
      />,
    );

    expect(screen.getByRole('button', { name: 'oa.ai.openPanel' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'mini-agent' })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: 'mini-agent' }));
    const drawer = screen.getByTestId('agent-drawer');
    expect(drawer.dataset.open).toBe('true');
    expect(drawer.dataset.pageId).toBe('meeting-room');
    expect(drawer.dataset.pageTitle).toBe('会议室');
    expect(drawer.dataset.prompt).toBe('mini-prompt');
    expect(screen.queryByRole('button', { name: 'mini-agent' })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'close-agent' }));
    fireEvent.click(screen.getByRole('button', { name: 'page-agent' }));
    expect(screen.getByTestId('agent-drawer').dataset.prompt).toBe('page-prompt');
  });

  it('closes and clears the previous page context when the route changes', async () => {
    const launcherRef = createRef<PageAgentLauncherHandle>();
    const view = render(
      <Harness launcherRef={launcherRef} pageId="todo" pageTitle="我的待办" />,
    );
    fireEvent.click(screen.getByRole('button', { name: 'page-agent' }));
    expect(screen.getByTestId('agent-drawer').dataset.open).toBe('true');

    view.rerender(
      <Harness launcherRef={launcherRef} pageId="visitor" pageTitle="访客预约" />,
    );

    await waitFor(() => {
      const drawer = screen.getByTestId('agent-drawer');
      expect(drawer.dataset.open).toBe('false');
      expect(drawer.dataset.pageId).toBe('visitor');
      expect(drawer.dataset.prompt).toBe('');
    });
  });

  it('keeps the independent AI workspace free of the global drawer and floating entry', () => {
    const launcherRef = createRef<PageAgentLauncherHandle>();
    render(
      <Harness launcherRef={launcherRef} pageId="ai-workspace" pageTitle="AI 工作空间" />,
    );

    expect(screen.queryByRole('button', { name: 'oa.ai.openPanel' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'mini-agent' })).toBeNull();
    expect(screen.queryByTestId('agent-drawer')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'page-agent' }));
    expect(screen.queryByTestId('agent-drawer')).toBeNull();
  });

  it('forwards a successful execution to the single page refresh boundary', () => {
    const launcherRef = createRef<PageAgentLauncherHandle>();
    const onPageRefresh = vi.fn();
    render(
      <Harness
        launcherRef={launcherRef}
        pageId="asset-ledger"
        pageTitle="资产台账"
        onPageRefresh={onPageRefresh}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: 'execution-completed' }));
    expect(onPageRefresh).toHaveBeenCalledTimes(1);
  });
});
