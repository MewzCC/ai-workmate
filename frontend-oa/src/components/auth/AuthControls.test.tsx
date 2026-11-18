import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { Form } from 'antd';
import { afterEach, expect, it, vi } from 'vitest';
import { PasswordInput } from './AuthControls';

afterEach(cleanup);

it('toggles a form-controlled password without changing its value or submitting', () => {
  const submit = vi.fn();
  render(<Form onFinish={submit}><Form.Item name="password"><PasswordInput aria-label="测试密码" type="password" autoComplete="current-password" /></Form.Item></Form>);
  const input = screen.getByLabelText('测试密码');
  fireEvent.change(input, { target: { value: 'Synthetic123' } });
  expect(input).toHaveAttribute('type', 'password');
  const show = screen.getByRole('button', { name: '查看密码' });
  expect(show.closest('.ant-input-suffix')).not.toBeNull();
  fireEvent.click(show);
  expect(input).toHaveAttribute('type', 'text');
  expect(input).toHaveValue('Synthetic123');
  expect(input).toHaveAttribute('autocomplete', 'current-password');
  const hide = screen.getByRole('button', { name: '隐藏密码' });
  expect(hide).toHaveAttribute('aria-pressed', 'true');
  fireEvent.click(hide);
  expect(input).toHaveAttribute('type', 'password');
  expect(input).toHaveValue('Synthetic123');
  expect(submit).not.toHaveBeenCalled();
});

it('keeps separate password fields independent and disables the toggle with the input', () => {
  render(<><PasswordInput aria-label="新密码" /><PasswordInput aria-label="确认密码" disabled /></>);
  const buttons = screen.getAllByRole('button', { name: '查看密码' });
  fireEvent.click(buttons[0]);
  expect(screen.getByLabelText('新密码')).toHaveAttribute('type', 'text');
  expect(screen.getByLabelText('确认密码')).toHaveAttribute('type', 'password');
  expect(buttons[1]).toBeDisabled();
});
