import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AiConfigurationCard from './AiConfigurationCard';
const mocks = vi.hoisted(() => ({
 user: { id: 1, tenantId: 1, permissionVersion: 1, roles: ['SUPER_ADMIN'], permissions: ['access:manage'] },
 get: vi.fn(), save: vi.fn(), channels: vi.fn(), saveChannel: vi.fn(), test: vi.fn(), discover: vi.fn(), success: vi.fn(),
}));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: mocks.user }) }));
vi.mock('@/lib/aiConfigurationApi', async (importOriginal) => ({
 ...await importOriginal<typeof import('@/lib/aiConfigurationApi')>(),
 aiConfigurationApi: mocks,
}));
vi.mock('@/lib/antdMessage', () => ({ message: { success: mocks.success } }));
vi.mock('react-i18next', () => { const t = (key: string, options?: { size?: string }) => key === 'aiConfiguration.contextPresetSize' ? options?.size : key; return { useTranslation: () => ({ t }) }; });
afterEach(cleanup);
function renderConfiguration() {
 render(<AiConfigurationCard renderPreferences={() => <div>AI user settings form</div>} />);
}
async function openEditor() {
 renderConfiguration();
 fireEvent.click(await screen.findByText('aiConfiguration.configureChannel'));
}
beforeEach(() => {
 vi.clearAllMocks(); mocks.user.roles = ['SUPER_ADMIN']; mocks.user.permissions = ['access:manage'];
 mocks.channels.mockResolvedValue([]);
 mocks.get.mockResolvedValue({ baseUrl: 'http://gateway.example/v1', model: 'custom/model', keyConfigured: true, version: 3, source: 'DATABASE' });
});
it.each([
 { roles: ['EMPLOYEE'], permissions: ['access:manage'] },
 { roles: ['SUPER_ADMIN'], permissions: [] },
])('shows only personal preferences without both platform permissions', ({ roles, permissions }) => {
 mocks.user.roles = roles; mocks.user.permissions = permissions; renderConfiguration();
 expect(mocks.channels).not.toHaveBeenCalled();
 expect(mocks.get).not.toHaveBeenCalled();
 expect(screen.queryByRole('tab', { name: 'aiConfiguration.channelsTitle' })).not.toBeInTheDocument();
 expect(screen.queryByRole('tab', { name: 'aiConfiguration.usage.title' })).not.toBeInTheDocument();
 expect(screen.getByRole('tabpanel', { name: 'aiConfiguration.userSettingsTitle' })).toHaveTextContent('AI user settings form');
});
it('fills provider URLs and allows an explicit custom name without reusing old credentials', async () => {
 await openEditor();
 await screen.findByDisplayValue('http://gateway.example/v1');
 for (const [provider, url] of [
  ['siliconflow', 'https://api.siliconflow.cn/v1'],
  ['deepseek', 'https://api.deepseek.com/v1'],
  ['mimo', 'https://api.xiaomimimo.com/v1'],
 ]) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'aiConfiguration.channel' }));
  fireEvent.click(await screen.findByText(`aiConfiguration.providers.${provider}`));
  expect(screen.getByLabelText('aiConfiguration.baseUrl')).toHaveValue(url);
  expect(document.querySelector('input[type=password]')).toHaveValue('');
  expect(screen.getByText('aiConfiguration.enterKey')).toBeInTheDocument();
 }
 fireEvent.mouseDown(screen.getByRole('combobox', { name: 'aiConfiguration.channel' }));
 fireEvent.click(await screen.findByText('aiConfiguration.customProvider'));
 const name = screen.getByLabelText('aiConfiguration.customName');
 fireEvent.change(name, { target: { value: 'My gateway' } });
 expect(name).toHaveValue('My gateway');
 fireEvent.change(screen.getByLabelText('aiConfiguration.baseUrl'), { target: { value: 'http://my-gateway.example/v1' } });
 expect(screen.getByLabelText('aiConfiguration.baseUrl')).toHaveValue('http://my-gateway.example/v1');
});
it('tests a saved channel directly from its card using only that channel credential reference', async () => {
 mocks.channels.mockResolvedValue([{ id: 2, name: 'Secondary', configuration: { baseUrl: 'http://second.example/v1', model: 'same', keyConfigured: true, version: 1, source: 'DATABASE', models: [] } }]);
 renderConfiguration();
 await screen.findByText('Secondary');
 expect(screen.queryByLabelText('aiConfiguration.baseUrl')).not.toBeInTheDocument();
 fireEvent.click(screen.getByText('aiConfiguration.testChannel'));
 await waitFor(() => expect(mocks.test).toHaveBeenCalledWith({ channelId: 2, baseUrl: 'http://second.example/v1', model: 'same' }));
});
it('opens a new channel with empty credentials and connection fields', async () => {
 await openEditor();
 await screen.findByDisplayValue('http://gateway.example/v1');
 fireEvent.click(screen.getByText('aiConfiguration.addChannel'));
 await waitFor(() => expect(screen.getByLabelText('aiConfiguration.baseUrl')).toHaveValue(''));
 expect(document.querySelector('input[type=password]')).toHaveValue('');
 expect(screen.getByText('aiConfiguration.enterKey')).toBeInTheDocument();
});
it('keeps existing credentials write-only and tests saved configuration', async () => {
 await openEditor();
 await screen.findByDisplayValue('http://gateway.example/v1');
 expect(document.querySelector('input[type=password]')).toHaveValue('');
 fireEvent.click(screen.getByText('aiConfiguration.test'));
 await waitFor(() => expect(mocks.test).toHaveBeenCalledOnce());
});
it('allows entering 384K output with a 32K context draft without clamping to 32767', async () => {
 await openEditor();
 await screen.findByDisplayValue('http://gateway.example/v1');
 const output=screen.getByRole('spinbutton', { name: 'aiConfiguration.outputFor' });
 fireEvent.change(output, { target: { value: '393216' } });
 fireEvent.blur(output);
 expect(output).toHaveValue('393216');
 expect(screen.getByRole('spinbutton', { name: 'aiConfiguration.contextFor' })).toHaveValue('32768');
 fireEvent.mouseDown(screen.getByRole('combobox', { name: 'aiConfiguration.contextPresetFor' }));
 fireEvent.click(await screen.findByText('1M'));
 expect(screen.getByRole('spinbutton', { name: 'aiConfiguration.contextFor' })).toHaveValue('1048576');
 expect(output).toHaveValue('393216');
 fireEvent.mouseDown(screen.getByRole('combobox', { name: 'aiConfiguration.outputPresetFor' }));
 fireEvent.click(await screen.findByText('8K'));
 expect(output).toHaveValue('8192');
 fireEvent.mouseDown(screen.getByRole('combobox', { name: 'aiConfiguration.outputPresetFor' }));
 fireEvent.click(await screen.findByText('384K'));
 expect(output).toHaveValue('393216');
});
it('tests unsaved provider fields with the selected model', async () => {
 await openEditor();
 await screen.findByDisplayValue('http://gateway.example/v1');
 const url = screen.getByLabelText('aiConfiguration.baseUrl');
 fireEvent.change(url, { target: { value: 'http://another-gateway.example/v1' } });
 fireEvent.click(screen.getByText('aiConfiguration.test'));
 await waitFor(() => expect(mocks.test).toHaveBeenCalledWith(expect.objectContaining({ baseUrl: 'http://another-gateway.example/v1', model: 'custom/model' })));
});
it('fetches upstream models and keeps them unselected until explicitly checked', async () => {
 mocks.discover.mockResolvedValue([{ id: 'new/model', contextWindow: 8192, maxOutputTokens: 2048, supportsImages: false }]);
 await openEditor(); await screen.findByDisplayValue('http://gateway.example/v1');
 fireEvent.click(screen.getByText('aiConfiguration.fetchModels'));
 await screen.findByText('new/model');
 expect(screen.getByRole('checkbox', { name: 'new/model' })).not.toBeChecked();
});

it('saves a dedicated embedding channel without a default chat model', async () => {
 mocks.saveChannel.mockImplementationOnce(async (id, name, configuration) => ({
  id, name, configuration: { ...configuration, keyConfigured: true, version: 1, source: 'DATABASE' },
 }));
 renderConfiguration();
 fireEvent.click(await screen.findByText('aiConfiguration.addChannel'));
 fireEvent.change(await screen.findByLabelText('aiConfiguration.customName'), { target: { value: 'Vector channel' } });
 fireEvent.change(screen.getByLabelText('aiConfiguration.baseUrl'), { target: { value: 'https://models.example/v1' } });
 fireEvent.change(screen.getByLabelText('chat.apiKeyLabel'), { target: { value: 'test-token' } });
 mocks.discover.mockResolvedValueOnce([{ id: 'vector-model', contextWindow: 32768, maxOutputTokens: 4096, supportsImages: false }]);
 fireEvent.click(screen.getByRole('button', { name: 'aiConfiguration.fetchModels' }));
 fireEvent.mouseDown(await screen.findByRole('combobox', { name: 'aiConfiguration.categoryFor' }));
 fireEvent.click(await screen.findByText('aiConfiguration.categories.EMBEDDING'));
 fireEvent.click(screen.getByRole('checkbox', { name: 'vector-model' }));
 fireEvent.click(screen.getByRole('checkbox', { name: 'aiConfiguration.requestDimensions' }));
 fireEvent.click(screen.getByRole('button', { name: 'common.save' }));
 await waitFor(() => expect(mocks.saveChannel).toHaveBeenCalledWith(2, 'Vector channel', expect.objectContaining({
  model: null,
  models: [expect.objectContaining({ id: 'vector-model', category: 'EMBEDDING', sendDimensions: true })],
 })));
});
