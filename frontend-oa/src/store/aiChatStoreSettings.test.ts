import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getChatPreferences, updateChatPreferences } from '@/lib/userSettingsApi';
import { useAiChatStore } from './aiChatStore';

vi.mock('@/lib/userSettingsApi', () => ({
  getChatPreferences: vi.fn(),
  updateChatPreferences: vi.fn(),
}));

const getPreferencesMock = vi.mocked(getChatPreferences);
const updatePreferencesMock = vi.mocked(updateChatPreferences);

describe('aiChatStore server settings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    useAiChatStore.setState({
      settings: { model: 'deepseek-v4-flash', kbId: null, maxContextRounds: 10, stream: true },
    });
  });

  it('uses initialized server preferences and discards legacy local settings', async () => {
    localStorage.setItem('workmeta-ai-chat-settings', JSON.stringify({
      model: 'deepseek-v4-flash', maxContextRounds: 4, stream: true,
    }));
    getPreferencesMock.mockResolvedValue({
      model: 'deepseek-v4-pro', maxContextRounds: 18, stream: false,
      forcePdfOcr: true, initialized: true,
    });

    await useAiChatStore.getState().hydrateSettings();

    expect(useAiChatStore.getState().settings).toMatchObject({
      model: 'deepseek-v4-pro', maxContextRounds: 18, stream: false,
    });
    expect(updatePreferencesMock).not.toHaveBeenCalled();
    expect(localStorage.getItem('workmeta-ai-chat-settings')).toBeNull();
    expect(localStorage.getItem('workmeta-ai-chat-settings-migrated')).toBe('true');
  });

  it('migrates legacy local preferences only when server settings are uninitialized', async () => {
    localStorage.setItem('workmeta-ai-chat-settings', JSON.stringify({
      model: 'deepseek-v4-pro', maxContextRounds: 14, stream: false,
    }));
    getPreferencesMock.mockResolvedValue({
      model: 'deepseek-v4-flash', maxContextRounds: 10, stream: true,
      forcePdfOcr: true, initialized: false,
    });
    updatePreferencesMock.mockResolvedValue({
      model: 'deepseek-v4-pro', maxContextRounds: 14, stream: false,
      forcePdfOcr: true, initialized: true,
    });

    await useAiChatStore.getState().hydrateSettings();

    expect(updatePreferencesMock).toHaveBeenCalledWith({
      model: 'deepseek-v4-pro', maxContextRounds: 14, stream: false, forcePdfOcr: true,
    });
    expect(useAiChatStore.getState().settings.model).toBe('deepseek-v4-pro');
  });
});
