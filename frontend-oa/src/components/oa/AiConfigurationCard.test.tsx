import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import AiConfigurationCard from './AiConfigurationCard';
const mocks = vi.hoisted(() => ({
 user: { id: 1, tenantId: 1, permissionVersion: 1, roles: ['SUPER_ADMIN'], permissions: ['access:manage'] },
 get: vi.fn(), save: vi.fn(), test: vi.fn(), discover: vi.fn(), success: vi.fn(),
}));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: mocks.user }) }));
vi.mock('@/lib/aiConfigurationApi', () => ({ aiConfigurationApi: mocks }));
vi.mock('@/lib/antdMessage', () => ({ message: { success: mocks.success } }));
vi.mock('react-i18next', () => { const t = (key: string) => key; return { useTranslation: () => ({ t }) }; });
afterEach(cleanup);
beforeEach(() => {
 vi.clearAllMocks(); mocks.user.roles = ['SUPER_ADMIN']; mocks.user.permissions = ['access:manage'];
 mocks.get.mockResolvedValue({ baseUrl: 'http://gateway.example/v1', model: 'custom/model', keyConfigured: true, version: 3, source: 'DATABASE' });
});
it('hides platform settings from ordinary users', () => {
 mocks.user.roles = ['EMPLOYEE']; render(<AiConfigurationCard />);
 expect(mocks.get).not.toHaveBeenCalled();
 expect(screen.queryByText('aiConfiguration.title')).not.toBeInTheDocument();
});
it('keeps existing credentials write-only and tests saved configuration', async () => {
 render(<AiConfigurationCard />);
 await screen.findByDisplayValue('http://gateway.example/v1');
 expect(document.querySelector('input[type=password]')).toHaveValue('');
 fireEvent.click(screen.getByText('aiConfiguration.test'));
 await waitFor(() => expect(mocks.test).toHaveBeenCalledOnce());
});
it('tests unsaved provider fields with the selected model', async () => {
 render(<AiConfigurationCard />);
 await screen.findByDisplayValue('http://gateway.example/v1');
 const url = screen.getByLabelText('aiConfiguration.baseUrl');
 fireEvent.change(url, { target: { value: 'http://another-gateway.example/v1' } });
 fireEvent.click(screen.getByText('aiConfiguration.test'));
 await waitFor(() => expect(mocks.test).toHaveBeenCalledWith(expect.objectContaining({ baseUrl: 'http://another-gateway.example/v1', model: 'custom/model' })));
});
it('fetches upstream models and keeps them unselected until explicitly checked', async () => {
 mocks.discover.mockResolvedValue([{ id: 'new/model', contextWindow: 8192, maxOutputTokens: 2048, supportsImages: false }]);
 render(<AiConfigurationCard />); await screen.findByDisplayValue('http://gateway.example/v1');
 fireEvent.click(screen.getByText('aiConfiguration.fetchModels'));
 await screen.findByText('new/model');
 expect(screen.getByRole('checkbox', { name: 'new/model' })).not.toBeChecked();
});
