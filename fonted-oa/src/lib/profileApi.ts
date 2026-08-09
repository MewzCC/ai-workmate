import i18n from '@/i18n';
import { buildApiHeaders } from '@/lib/apiHeaders';
import type { AuthUser } from './authApi';

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
}

export interface WallpaperResponse {
  wallpaperUrl: string | null;
}

async function parse<T>(responsePromise: Promise<Response>, fallbackMessage: string, requireData = true): Promise<T> {
  const response = await responsePromise;
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (!response.ok || !result || result.code !== 200 || (requireData && !result.data)) {
    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    throw new Error(result?.message || fallbackMessage);
  }
  return result.data as T;
}

export const profileApi = {
  update: (name: string) => parse<AuthUser>(fetch('/api/profile', {
    method: 'PUT',
    credentials: 'include',
    headers: buildApiHeaders(),
    body: JSON.stringify({ name }),
  }), i18n.t('errors.profile.updateFailed')),
  changePassword: (oldPassword: string, newPassword: string) => parse<void>(fetch('/api/profile/password', {
    method: 'POST',
    credentials: 'include',
    headers: buildApiHeaders(),
    body: JSON.stringify({ oldPassword, newPassword }),
  }), i18n.t('errors.profile.passwordChangeFailed'), false),
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return parse<AuthUser>(fetch('/api/profile/avatar', {
      method: 'POST',
      credentials: 'include',
      headers: buildApiHeaders(false),
      body: form,
    }), i18n.t('errors.profile.avatarUploadFailed'));
  },
  deleteAvatar: () => parse<AuthUser>(fetch('/api/profile/avatar', {
    method: 'DELETE',
    credentials: 'include',
    headers: buildApiHeaders(false),
  }), i18n.t('errors.profile.avatarDeleteFailed')),
  getWallpaper: () => parse<WallpaperResponse>(fetch('/api/profile/wallpaper', {
    credentials: 'include',
    headers: buildApiHeaders(false),
  }), i18n.t('errors.profile.wallpaperLoadFailed')),
  uploadWallpaper: (file: Blob) => {
    const form = new FormData();
    form.append('file', file, 'wallpaper.webp');
    return parse<WallpaperResponse>(fetch('/api/profile/wallpaper', {
      method: 'POST',
      credentials: 'include',
      headers: buildApiHeaders(false),
      body: form,
    }), i18n.t('errors.profile.wallpaperUploadFailed'));
  },
  deleteWallpaper: () => parse<WallpaperResponse>(fetch('/api/profile/wallpaper', {
    method: 'DELETE',
    credentials: 'include',
    headers: buildApiHeaders(false),
  }), i18n.t('errors.profile.wallpaperDeleteFailed')),
};
