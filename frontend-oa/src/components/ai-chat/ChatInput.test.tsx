import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ChatInput from './ChatInput';

afterEach(cleanup);

function props() {
  return { pending: [], uploading: [], generating: false, mode: 'auto' as const,
    onModeChange: vi.fn(), onUpload: vi.fn(), onRemoveAttachment: vi.fn(), onSend: vi.fn(), onStop: vi.fn() };
}

describe('Workspace composer modes', () => {
  it('offers automatic, explicit chat and governed operation modes', () => {
    const handlers = props();
    render(<ChatInput {...handlers} />);
    expect(screen.getByRole('radio', { name: /自动/ })).toBeChecked();
    fireEvent.click(screen.getByRole('radio', { name: /对话/ }));
    expect(handlers.onModeChange).toHaveBeenCalledWith('chat');
    fireEvent.click(screen.getByRole('radio', { name: /OA 操作/ }));
    expect(handlers.onModeChange).toHaveBeenCalledWith('operation');
  });

  it('sends trimmed text once and clears the input after Enter', () => {
    const handlers = props();
    render(<ChatInput {...handlers} />);
    const input = screen.getByRole('textbox');
    fireEvent.change(input, { target: { value: ' 查询预算 ' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    expect(handlers.onSend).toHaveBeenCalledOnce();
    expect(handlers.onSend).toHaveBeenCalledWith('查询预算');
    expect(input).toHaveValue('');
  });

  it('prevents mode changes and duplicate sends while generating, and exposes stop', () => {
    const handlers = props();
    render(<ChatInput {...handlers} generating />);
    screen.getAllByRole('radio').forEach((radio) => expect(radio).toBeDisabled());
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '查询预算' } });
    fireEvent.keyDown(screen.getByRole('textbox'), { key: 'Enter' });
    expect(handlers.onSend).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: /停止/ }));
    expect(handlers.onStop).toHaveBeenCalledOnce();
  });
});
