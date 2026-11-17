import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { adminAssetsApi, type SealUsageDocument } from './adminAssetsApi';
const NativeURL = URL;
const fetcher = vi.fn(), createUrl = vi.fn(), revokeUrl = vi.fn();
const document: SealUsageDocument = { id: 1, sealUsageId: 3, displayName: 'synthetic.txt', mimeType: 'text/plain', fileSize: 4,
  uploadedByUserId: 7, contentUrl: '/api/admin-assets/seal-usages/3/documents/1/content', createdAt: '2026-10-30T09:00:00' };
beforeEach(() => {
  vi.resetAllMocks(); vi.stubGlobal('fetch', fetcher);
  vi.stubGlobal('URL', Object.assign(class extends NativeURL {}, { createObjectURL: createUrl, revokeObjectURL: revokeUrl }));
  createUrl.mockReturnValue('blob:synthetic-file'); vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
});
afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it('does not request a file from a captured stale archive', async () => {
  await adminAssetsApi.downloadSealUsageDocument(document, () => false);
  expect(fetcher).not.toHaveBeenCalled(); expect(createUrl).not.toHaveBeenCalled();
});
it('discards a body delivered after the current archive or identity changes', async () => {
  let deliver!: (blob: Blob) => void;
  const body = new Promise<Blob>(resolve => { deliver = resolve; });
  fetcher.mockResolvedValue({ ok: true, status: 200, blob: () => body });
  let current = true;
  const download = adminAssetsApi.downloadSealUsageDocument(document, () => current);
  await Promise.resolve(); current = false; deliver(new Blob(['safe'])); await download;
  expect(createUrl).not.toHaveBeenCalled(); expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled();
});
it('keeps the authorized download, locale headers and object URL cleanup', async () => {
  fetcher.mockResolvedValue({ ok: true, status: 200, blob: async () => new Blob(['safe']) });
  await adminAssetsApi.downloadSealUsageDocument(document);
  expect(fetcher).toHaveBeenCalledWith(document.contentUrl, expect.objectContaining({ credentials: 'include', headers: expect.objectContaining({ 'Accept-Language': 'zh-CN' }) }));
  expect(HTMLAnchorElement.prototype.click).toHaveBeenCalledTimes(1); expect(revokeUrl).toHaveBeenCalledWith('blob:synthetic-file');
});

it('does not emit a stale authentication failure into another archive or identity', async () => {
  let deliver!: (response: Response) => void;
  fetcher.mockReturnValue(new Promise<Response>(resolve => { deliver = resolve; }));
  const dispatch = vi.spyOn(window, 'dispatchEvent');
  let current = true;
  const download = adminAssetsApi.downloadSealUsageDocument(document, () => current);
  current = false; deliver(new Response('', { status: 401 })); await download;
  expect(dispatch).not.toHaveBeenCalled();
});

it('suppresses an old upload authentication response without pretending to undo the server write', async () => {
  let deliver!: (response: Response) => void;
  fetcher.mockReturnValue(new Promise<Response>(resolve => { deliver = resolve; }));
  const dispatch = vi.spyOn(window, 'dispatchEvent');
  let current = true;
  const upload = adminAssetsApi.uploadSealUsageDocument(3, new File(['safe'], 'synthetic.txt'), () => current);
  current = false; deliver(new Response('', { status: 401 }));
  await expect(upload).rejects.toMatchObject({ name: 'AbortError' }); expect(dispatch).not.toHaveBeenCalled();
});
