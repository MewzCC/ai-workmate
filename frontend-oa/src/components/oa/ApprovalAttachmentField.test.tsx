import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { t } from 'i18next';
import type { ApprovalAttachment } from '@/lib/approvalAttachmentApi';
import { ApprovalAttachmentField } from './ApprovalAttachmentField';

const api = vi.hoisted(() => ({ upload: vi.fn(), metadata: vi.fn(), download: vi.fn(), error: vi.fn() }));
vi.mock('@/lib/approvalAttachmentApi', () => ({ approvalAttachmentApi: api }));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error } }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => ({ user: {
  id: 7, tenantId: 1, permissionVersion: 1, permissions: ['approval:create'], roles: ['USER'], dataScopes: ['SELF'],
} }) }));

const changed = vi.fn(), busy = vi.fn(), save = vi.fn();
const receipt: ApprovalAttachment = { id: '9', fieldName: 'proof', fieldType: 'file', displayName: 'proof.txt', mimeType: 'text/plain', fileSize: 12, createdAt: '2026-11-12T12:00:00' };
function view(scope = 'owner:draft1', active = true, applicationId: number | undefined = 11) {
  return <ConfigProvider><ApprovalAttachmentField applicationId={applicationId} fieldName="proof" fieldType="file"
    active={active} disabled={false} scope={scope} value={[]} onChange={changed} onBusy={busy} saveDraft={save} /></ConfigProvider>;
}
function chooseFile() {
  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  expect(input).not.toBeNull();
  fireEvent.change(input!, { target: { files: [new File(['Owned file'], 'proof.txt', { type: 'text/plain' })] } });
}
beforeEach(() => { vi.resetAllMocks(); api.upload.mockResolvedValue(receipt); });
afterEach(cleanup);

it('publishes only the acknowledged attachment identifier and releases the upload lock', async () => {
  render(view()); chooseFile();
  await waitFor(() => expect(changed).toHaveBeenCalledWith(['9']));
  expect(api.upload).toHaveBeenCalledWith(11, 'proof', expect.any(File), expect.any(AbortSignal));
  expect(busy.mock.calls).toEqual([[true], [false]]);
});

it('aborts an old upload and discards its receipt after the form scope closes', async () => {
  let resolve!: (attachment: ApprovalAttachment) => void;
  api.upload.mockReturnValue(new Promise(done => { resolve = done; }));
  const page = render(view()); chooseFile();
  await waitFor(() => expect(api.upload).toHaveBeenCalledTimes(1));
  const signal = api.upload.mock.calls[0][3] as AbortSignal;
  page.rerender(view('other-owner:draft2', false));
  expect(signal.aborted).toBe(true);
  await act(async () => resolve(receipt));
  expect(changed).not.toHaveBeenCalled();
  expect(api.error).not.toHaveBeenCalled();
});

it('does not bind a receipt returned for another attachment field', async () => {
  api.upload.mockResolvedValue({ ...receipt, fieldName: 'other' });
  render(view()); chooseFile();
  await waitFor(() => expect(api.error).toHaveBeenCalled());
  expect(changed).not.toHaveBeenCalled();
  expect(api.upload).toHaveBeenCalledTimes(1);
});

it('requires an existing saved draft before showing the upload control', () => {
  render(<ConfigProvider><ApprovalAttachmentField fieldName="proof" fieldType="file" active disabled={false}
    scope="new-form" saveDraft={save} onBusy={busy} /></ConfigProvider>);
  fireEvent.click(screen.getByRole('button', { name: t('approval.attachments.saveFirst') }));
  expect(save).toHaveBeenCalledOnce();
  expect(document.querySelector('input[type="file"]')).toBeNull();
  expect(api.upload).not.toHaveBeenCalled();
});
