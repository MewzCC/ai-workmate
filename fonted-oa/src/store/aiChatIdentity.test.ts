import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({ listConversations: vi.fn(), listMessages: vi.fn(), deleteConversation: vi.fn() }));
const notifications = vi.hoisted(() => ({ error: vi.fn(), info: vi.fn(), warning: vi.fn() }));
vi.mock('@/lib/chatApi', async load => ({ ...await load<typeof import('@/lib/chatApi')>(), ...api }));
vi.mock('@/lib/antdMessage', () => ({ message: notifications }));
import { bindChatIdentity, useAiChatStore } from './aiChatStore';

describe('authenticated chat session isolation', () => {
  beforeEach(() => {
    vi.clearAllMocks(); bindChatIdentity(null); bindChatIdentity('tenant-a:user-a');
    api.listConversations.mockResolvedValue([]); api.listMessages.mockResolvedValue([]);
  });

  it('preserves the same identity while replacing user and tenant caches', () => {
    useAiChatStore.setState({ activeId: 7, draftMode: true });
    const first = useAiChatStore.getState();
    bindChatIdentity('tenant-a:user-a');
    expect(useAiChatStore.getState()).toBe(first);
    bindChatIdentity('tenant-b:user-a');
    expect(useAiChatStore.getState().activeId).toBeNull();
    expect(useAiChatStore.getState().draftMode).toBe(false);
    expect(useAiChatStore.getState()).not.toBe(first);
  });

  it('ignores an old identity response and does not preload its messages after logout', async () => {
    let resolve!: (value: { id: number; title: string; model: string; createdAt: string; updatedAt: string }[]) => void;
    api.listConversations.mockReturnValueOnce(new Promise(done => { resolve = done; }));
    const pending = useAiChatStore.getState().loadConversations();
    bindChatIdentity(null); bindChatIdentity('tenant-a:user-b');
    resolve([{ id: 7, title: 'Private previous conversation', model: 'deepseek-v4-flash', createdAt: '', updatedAt: '' }]);
    await pending;
    expect(useAiChatStore.getState().conversations).toEqual([]);
    expect(useAiChatStore.getState().messagesByConversation).toEqual({});
    expect(api.listMessages).not.toHaveBeenCalled();
  });

  it('rejects a stale captured deletion action before sending a request with a new login', async () => {
    const stale = useAiChatStore.getState().remove;
    bindChatIdentity('tenant-a:user-b');
    await expect(stale(7)).rejects.toMatchObject({ name: 'AbortError' });
    expect(api.deleteConversation).not.toHaveBeenCalled();
  });

  it('does not show a previous accounts late read error under the new account', async () => {
    let reject!: (reason: Error) => void;
    api.listMessages.mockReturnValueOnce(new Promise((_, fail) => { reject = fail; }));
    const pending = useAiChatStore.getState().selectConversation(7);
    bindChatIdentity('tenant-a:user-b');
    reject(new Error('Private error from the previous account'));
    await pending;
    expect(notifications.error).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().activeId).toBeNull();
    expect(useAiChatStore.getState().messagesByConversation).toEqual({});
  });
});
