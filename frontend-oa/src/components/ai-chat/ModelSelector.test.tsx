import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, it, vi } from 'vitest';
import ModelSelector from './ModelSelector';
vi.mock('react-i18next', () => { const t = (key: string) => key; return { useTranslation: () => ({ t }) }; });
vi.mock('@/lib/aiConfigurationApi', () => ({ aiConfigurationApi: { catalog: vi.fn().mockResolvedValue([{ id: 'enabled/model' }]) } }));
afterEach(cleanup);
it('loads enabled models into a searchable dropdown', async () => {
 render(<ModelSelector value="default" disabled={false} onChange={vi.fn()} />);
 fireEvent.focus(screen.getByRole('combobox'));
 fireEvent.keyDown(screen.getByRole('combobox'), { key: 'ArrowDown', keyCode: 40 });
 expect(await screen.findByRole('option', { name: 'enabled/model' })).toBeInTheDocument();
});
it('does not persist a typed search query as an arbitrary model ID', () => {
 const changed = vi.fn(); render(<ModelSelector value="default" disabled={false} onChange={changed} />);
 fireEvent.change(screen.getByRole('combobox'), { target: { value: 'unselected/model' } });
 expect(changed).not.toHaveBeenCalled();
});
