import { App, ConfigProvider } from 'antd';
import { act, cleanup, renderHook, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { useUiFormPreview } from './UiFormPreview';

const wrapper = ({ children }: { children: React.ReactNode }) => <ConfigProvider><App>{children}</App></ConfigProvider>;
afterEach(cleanup);

describe('owned private form previews', () => {
  it('removes only the changed form preview and retains another owner', async () => {
    const first = renderHook(({ scope }) => useUiFormPreview(scope), { wrapper, initialProps: { scope: 'actor-a:form-one' } });
    const second = renderHook(() => useUiFormPreview('actor-a:form-two'), { wrapper });
    act(() => {
      first.result.current({ reason: 'private-first-form', hidden: 'must-not-render' }, { reason: 'Reason' });
      second.result.current({ reason: 'private-second-form' }, { reason: 'Reason' });
    });
    expect(await screen.findByText('private-first-form')).toBeTruthy();
    expect(await screen.findByText('private-second-form')).toBeTruthy();
    expect(screen.queryByText('must-not-render')).toBeNull();
    first.rerender({ scope: 'actor-b:replacement-form' });
    await waitFor(() => expect(screen.queryByText('private-first-form')).toBeNull());
    expect(screen.getByText('private-second-form')).toBeTruthy();
  });
  it('removes private preview data when the owning page unmounts', async () => {
    const hook = renderHook(() => useUiFormPreview('actor-a:form'), { wrapper });
    act(() => { hook.result.current({ reason: 'private-unmounted-form' }, { reason: 'Reason' }); });
    expect(await screen.findByText('private-unmounted-form')).toBeTruthy();
    hook.unmount();
    await waitFor(() => expect(screen.queryByText('private-unmounted-form')).toBeNull());
  });
});
