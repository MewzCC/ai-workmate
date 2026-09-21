import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import {
  PageAgentContextProvider,
  sanitizePageAgentContext,
  usePageAgentContextSnapshot,
  usePublishPageAgentContext,
} from './PageAgentContext';

function Publisher({ context }: { context: Record<string, unknown> }) {
  usePublishPageAgentContext(context);
  return null;
}

function Snapshot() {
  const value = usePageAgentContextSnapshot();
  return <output data-testid="snapshot">{JSON.stringify(value)}</output>;
}

describe('PageAgentContext', () => {
  afterEach(cleanup);

  it('keeps only bounded scalar business context and drops identity or executable fields', () => {
    expect(sanitizePageAgentContext({
      status: 'PENDING',
      page: 2,
      overdue: false,
      tenantId: 9,
      userId: 12,
      permissionCode: 'admin',
      callbackUrl: 'https://example.invalid',
      nested: { unsafe: true },
      invalidNumber: Number.POSITIVE_INFINITY,
      TooLongNameBecauseItExceedsTheFortyCharacterBoundary: 'ignored',
    })).toEqual({ overdue: false, page: 2, status: 'PENDING' });
  });

  it('merges independent page fragments and removes an unmounted fragment', async () => {
    const view = render(
      <PageAgentContextProvider pageId="meeting-room">
        <Publisher context={{ keyword: '研发', roomStatus: 'OPEN' }} />
        <Publisher context={{ from: '2026-09-01', to: '2026-09-30' }} />
        <Snapshot />
      </PageAgentContextProvider>,
    );

    await waitFor(() => expect(JSON.parse(screen.getByTestId('snapshot').textContent || '{}')).toEqual({
      keyword: '研发',
      roomStatus: 'OPEN',
      from: '2026-09-01',
      to: '2026-09-30',
    }));

    view.rerender(
      <PageAgentContextProvider pageId="meeting-room">
        <Publisher context={{ keyword: '研发', roomStatus: 'OPEN' }} />
        <Snapshot />
      </PageAgentContextProvider>,
    );

    await waitFor(() => expect(JSON.parse(screen.getByTestId('snapshot').textContent || '{}')).toEqual({
      keyword: '研发',
      roomStatus: 'OPEN',
    }));
  });

  it('clears the previous snapshot when the route page changes', async () => {
    const view = render(
      <PageAgentContextProvider pageId="todo">
        <Publisher context={{ status: 'PENDING', page: 3 }} />
        <Snapshot />
      </PageAgentContextProvider>,
    );
    await waitFor(() => expect(screen.getByTestId('snapshot').textContent).toContain('PENDING'));

    view.rerender(
      <PageAgentContextProvider pageId="visitor-booking">
        <Snapshot />
      </PageAgentContextProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('snapshot').textContent).toBe('{}'));
  });
});
