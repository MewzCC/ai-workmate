import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import type { AuthUser } from '@/lib/authApi';
import ProfileSettingsModal from './ProfileSettingsModal';
import AppearanceDrawer from '@/components/oa/AppearanceDrawer';

const auth = vi.hoisted(() => ({ user: undefined as AuthUser | undefined, setUser: vi.fn() }));
const api = vi.hoisted(() => ({ update: vi.fn(), uploadAvatar: vi.fn(), deleteAvatar: vi.fn(), changePassword: vi.fn(), uploadWallpaper: vi.fn(), deleteWallpaper: vi.fn() }));
const wallpaper = vi.hoisted(() => ({ createCroppedWallpaper: vi.fn(), createWallpaperSource: vi.fn(), releaseWallpaperSource: vi.fn() }));
const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }));
vi.mock('@/components/auth/AuthProvider', () => ({ useAuth: () => auth }));
vi.mock('@/lib/profileApi', () => ({ profileApi: api }));
vi.mock('@/lib/wallpaper', () => wallpaper);
vi.mock('@/lib/antdMessage', () => ({ message: messages }));
vi.mock('@/components/oa/WallpaperCropModal', async () => {
  const { Button } = await import('antd');
  return { default: ({ open, onConfirm, onCancel }: { open: boolean; onConfirm: (crop: { x: number; y: number; width: number; height: number }, rotation: number) => void; onCancel: () => void }) => open
    ? <section aria-label="synthetic crop controls"><Button onClick={() => onConfirm({ x: 0, y: 0, width: 80, height: 60 }, 0)}>Run synthetic crop</Button><Button onClick={onCancel}>Close synthetic crop</Button></section> : null };
});

const NativeURL = URL;
const close = vi.fn(), onWallpaper = vi.fn(), fetcher = vi.fn(), revoke = vi.fn();
function user(id = 7, permissionVersion = 1): AuthUser { return { id, tenantId: 1, name: `Synthetic user ${id}`, email: `user${id}@example.invalid`, role: 'EMPLOYEE', roles: ['EMPLOYEE'],
  avatarUrl: `/api/profile/avatar?owner=${id}`, permissions: [], dataScopes: ['SELF'], permissionVersion }; }
function deferred<T>() { let resolve!: (value: T) => void; const promise = new Promise<T>(done => { resolve = done; }); return { promise, resolve }; }
function Profile({ open = true }: { open?: boolean }) { return <ConfigProvider locale={zhCN}><App><ProfileSettingsModal open={open} onClose={close} /></App></ConfigProvider>; }
function Appearance({ open = true }: { open?: boolean }) { return <ConfigProvider locale={zhCN}><App><AppearanceDrawer open={open} themes={[]} currentTheme="enterprise-blue" aiMiniEnabled={false}
  wallpaper="/api/profile/wallpaper/content" wallpaperOpacity={0.3} wallpaperBlur={0} onClose={close} onThemeChange={vi.fn()} onAiMiniChange={vi.fn()}
  onWallpaperChange={onWallpaper} onWallpaperOpacityChange={vi.fn()} onWallpaperBlurChange={vi.fn()} /></App></ConfigProvider>; }
async function profileDialog() { return screen.findByRole('dialog'); }
function chooseAvatar(form: HTMLElement) { const input = form.querySelector<HTMLInputElement>('input[type="file"]'); expect(input).toBeTruthy();
  fireEvent.change(input!, { target: { files: [new File(['synthetic'], 'synthetic.png', { type: 'image/png' })] } }); }
beforeEach(() => {
  vi.resetAllMocks(); auth.user = user();
  vi.stubGlobal('URL', Object.assign(class extends NativeURL {}, { createObjectURL: vi.fn(() => 'blob:synthetic-avatar'), revokeObjectURL: revoke }));
  vi.stubGlobal('fetch', fetcher); fetcher.mockResolvedValue({ blob: async () => new Blob(['synthetic']) });
  wallpaper.createCroppedWallpaper.mockResolvedValue('blob:synthetic-crop'); wallpaper.createWallpaperSource.mockReturnValue('blob:synthetic-source');
  window.localStorage.setItem('workmeta-oa-wallpaper', 'data:image/png;base64,synthetic-retained-local');
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); window.localStorage.removeItem('workmeta-oa-wallpaper'); });

it('stops a previous account save before avatar upload and never rebinds the new user', async () => {
  const pending = deferred<AuthUser>(); api.update.mockReturnValueOnce(pending.promise);
  const view = render(<Profile />); const form = await profileDialog(); chooseAvatar(form);
  fireEvent.change(within(form).getByLabelText('姓名'), { target: { value: 'Old account edit' } });
  fireEvent.click(within(form).getByRole('button', { name: '保存资料' })); await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
  expect(within(form).getByLabelText('姓名')).toBeDisabled();
  auth.user = user(8); view.rerender(<Profile />);
  expect((within(await profileDialog()).getByLabelText('姓名') as HTMLInputElement).value).toBe('Synthetic user 8');
  await act(async () => pending.resolve(user()));
  expect(api.uploadAvatar).not.toHaveBeenCalled(); expect(auth.setUser).not.toHaveBeenCalled();
  expect(close).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
});

it('keeps a replacement profile draft after an old save returns', async () => {
  const pending = deferred<AuthUser>(); api.update.mockReturnValueOnce(pending.promise);
  const view = render(<Profile />); let form = await profileDialog();
  fireEvent.click(within(form).getByRole('button', { name: '保存资料' })); await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
  fireEvent.click(await within(form).findByRole('button', { name: '关 闭' }));
  view.rerender(<Profile open={false} />); view.rerender(<Profile />); form = await profileDialog();
  fireEvent.change(within(form).getByLabelText('姓名'), { target: { value: 'Replacement profile draft' } });
  await act(async () => pending.resolve(user()));
  expect((within(form).getByLabelText('姓名') as HTMLInputElement).value).toBe('Replacement profile draft');
  expect(auth.setUser).not.toHaveBeenCalled(); expect(close).toHaveBeenCalledTimes(1);
});

it('preserves an unsaved name on a same-account permission refresh and ignores its older in-flight save', async () => {
  const pending = deferred<AuthUser>(); api.update.mockReturnValueOnce(pending.promise);
  const view = render(<Profile />); const form = await profileDialog();
  fireEvent.change(within(form).getByLabelText('姓名'), { target: { value: 'Unsent current draft' } });
  auth.user = user(7, 2); view.rerender(<Profile />);
  expect((within(form).getByLabelText('姓名') as HTMLInputElement).value).toBe('Unsent current draft');
  fireEvent.click(within(form).getByRole('button', { name: '保存资料' })); await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1));
  auth.user = user(7, 3); view.rerender(<Profile />); await act(async () => pending.resolve(user(7, 2)));
  expect(auth.setUser).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
});

it('updates a matching current profile and avatar through the actual sequential client contract', async () => {
  api.update.mockResolvedValue(user()); api.uploadAvatar.mockResolvedValue({ ...user(), avatarUrl: '/api/profile/avatar?updated=true' });
  render(<Profile />); const form = await profileDialog(); chooseAvatar(form);
  fireEvent.click(within(form).getByRole('button', { name: '保存资料' }));
  await waitFor(() => expect(auth.setUser).toHaveBeenCalledWith(expect.objectContaining({ id: 7, avatarUrl: '/api/profile/avatar?updated=true' })));
  expect(api.uploadAvatar).toHaveBeenCalledTimes(1); expect(close).toHaveBeenCalledTimes(1);
});

it('does not accept a mismatched profile owner in a successful response', async () => {
  api.update.mockResolvedValue(user(8)); render(<Profile />); const form = await profileDialog();
  fireEvent.click(within(form).getByRole('button', { name: '保存资料' })); await waitFor(() => expect(messages.error).toHaveBeenCalled());
  expect(auth.setUser).not.toHaveBeenCalled(); expect(close).not.toHaveBeenCalled();
});

it('captures the submitting identity before validation can finish in a replacement account', async () => {
  const view = render(<Profile />); const form = await profileDialog();
  fireEvent.click(within(form).getByRole('button', { name: '保存资料' }));
  auth.user = user(8); view.rerender(<Profile />);
  await act(async () => { await Promise.resolve(); });
  expect(api.update).not.toHaveBeenCalled(); expect(api.uploadAvatar).not.toHaveBeenCalled(); expect(auth.setUser).not.toHaveBeenCalled();
});

it('retains native form submission through the same protected validation path', async () => {
  api.update.mockResolvedValue(user()); render(<Profile />); const dialog = await profileDialog();
  const form = dialog.querySelector('form'); expect(form).toBeTruthy(); fireEvent.submit(form!);
  await waitFor(() => expect(api.update).toHaveBeenCalledTimes(1)); expect(auth.setUser).toHaveBeenCalledTimes(1);
});

it('ignores a previous avatar deletion after account replacement', async () => {
  const pending = deferred<AuthUser>(); api.deleteAvatar.mockReturnValueOnce(pending.promise);
  const view = render(<Profile />); const form = await profileDialog();
  fireEvent.click(within(form).getByRole('button', { name: /移\s*除/ })); await waitFor(() => expect(api.deleteAvatar).toHaveBeenCalledTimes(1));
  auth.user = user(8); view.rerender(<Profile />); await act(async () => pending.resolve(user()));
  expect(auth.setUser).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
});

it('clears password drafts on account replacement and ignores a previous password completion', async () => {
  const pending = deferred<void>(); api.changePassword.mockReturnValueOnce(pending.promise);
  const view = render(<Profile />); let form = await profileDialog();
  fireEvent.click(within(form).getByRole('tab', { name: '修改密码' }));
  fireEvent.change(within(form).getByLabelText('当前密码'), { target: { value: 'SyntheticOld123' } });
  fireEvent.change(within(form).getByLabelText('新密码', { exact: true }), { target: { value: 'SyntheticNew123' } });
  fireEvent.change(within(form).getByLabelText('确认新密码'), { target: { value: 'SyntheticNew123' } });
  fireEvent.click(within(form).getByRole('button', { name: '修改密码' }));
  await waitFor(() => expect(api.changePassword).toHaveBeenCalledTimes(1));
  expect(within(form).getByLabelText('当前密码')).toBeDisabled();
  auth.user = user(8); view.rerender(<Profile />); form = await profileDialog();
  fireEvent.click(within(form).getByRole('tab', { name: '修改密码' }));
  expect((within(form).getByLabelText('当前密码') as HTMLInputElement).value).toBe('');
  expect((within(form).getByLabelText('新密码', { exact: true }) as HTMLInputElement).value).toBe('');
  await act(async () => pending.resolve());
  expect(close).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
});

it('discards an old crop before starting a request for the replacement account', async () => {
  const pending = deferred<string>(); wallpaper.createCroppedWallpaper.mockReturnValueOnce(pending.promise);
  const view = render(<Appearance />); fireEvent.click(screen.getByRole('button', { name: '重新裁剪' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Run synthetic crop' }));
  await waitFor(() => expect(wallpaper.createCroppedWallpaper).toHaveBeenCalledTimes(1));
  auth.user = user(8); view.rerender(<Appearance />); await act(async () => pending.resolve('blob:old-crop'));
  expect(fetcher).not.toHaveBeenCalled(); expect(api.uploadWallpaper).not.toHaveBeenCalled(); expect(onWallpaper).not.toHaveBeenCalled();
});

it('stops before wallpaper upload if the local image body arrives after closing', async () => {
  const pending = deferred<Blob>(); fetcher.mockResolvedValue({ blob: () => pending.promise });
  const view = render(<Appearance />); fireEvent.click(screen.getByRole('button', { name: '重新裁剪' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Run synthetic crop' })); await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1));
  view.rerender(<Appearance open={false} />); view.rerender(<Appearance />);
  await act(async () => pending.resolve(new Blob(['synthetic'])));
  expect(api.uploadWallpaper).not.toHaveBeenCalled(); expect(onWallpaper).not.toHaveBeenCalled();
});

it('does not apply an old uploaded wallpaper or erase the replacement account local legacy value', async () => {
  const pending = deferred<{ wallpaperUrl: string }>(); api.uploadWallpaper.mockReturnValueOnce(pending.promise);
  const view = render(<Appearance />); fireEvent.click(screen.getByRole('button', { name: '重新裁剪' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Run synthetic crop' })); await waitFor(() => expect(api.uploadWallpaper).toHaveBeenCalledTimes(1));
  auth.user = user(8); view.rerender(<Appearance />);
  await act(async () => pending.resolve({ wallpaperUrl: '/api/profile/wallpaper/content?old=true' }));
  expect(onWallpaper).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
  expect(window.localStorage.getItem('workmeta-oa-wallpaper')).toBe('data:image/png;base64,synthetic-retained-local');
});

it('ignores an old wallpaper removal and closes immediately without a fake save timer', async () => {
  const pending = deferred<void>(); api.deleteWallpaper.mockReturnValueOnce(pending.promise);
  const view = render(<Appearance />); fireEvent.click(screen.getByRole('button', { name: '清除壁纸' }));
  await waitFor(() => expect(api.deleteWallpaper).toHaveBeenCalledTimes(1));
  auth.user = user(8); view.rerender(<Appearance />); await act(async () => pending.resolve());
  expect(onWallpaper).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole('button', { name: '关 闭' }));
  expect(close).toHaveBeenCalledTimes(1); expect(messages.success).not.toHaveBeenCalled();
});

it('applies the current cropped wallpaper only after the upload really resolves', async () => {
  const pending = deferred<{ wallpaperUrl: string }>(); api.uploadWallpaper.mockReturnValueOnce(pending.promise);
  render(<Appearance />); fireEvent.click(screen.getByRole('button', { name: '重新裁剪' }));
  fireEvent.click(await screen.findByRole('button', { name: 'Run synthetic crop' }));
  await waitFor(() => expect(api.uploadWallpaper).toHaveBeenCalledTimes(1));
  expect(onWallpaper).not.toHaveBeenCalled(); expect(messages.success).not.toHaveBeenCalled();
  await act(async () => pending.resolve({ wallpaperUrl: '/api/profile/wallpaper/content?updated=true' }));
  expect(onWallpaper).toHaveBeenCalledWith('/api/profile/wallpaper/content?updated=true');
  expect(window.localStorage.getItem('workmeta-oa-wallpaper')).toBeNull();
  expect(messages.success).toHaveBeenCalledTimes(1);
});
