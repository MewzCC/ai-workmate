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

async function parse<T>(responsePromise: Promise<Response>, fallbackMessage: string): Promise<T> {
  const response = await responsePromise;
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (!response.ok || !result || result.code !== 200 || !result.data) {
    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    throw new Error(result?.message || fallbackMessage);
  }
  return result.data;
}

export const profileApi = {
  update: (name: string) => parse<AuthUser>(fetch('/api/profile', {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  }), '用户资料更新失败'),
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return parse<AuthUser>(fetch('/api/profile/avatar', {
      method: 'POST',
      credentials: 'include',
      body: form,
    }), '头像上传失败');
  },
  deleteAvatar: () => parse<AuthUser>(fetch('/api/profile/avatar', {
    method: 'DELETE',
    credentials: 'include',
  }), '头像删除失败'),
  getWallpaper: () => parse<WallpaperResponse>(fetch('/api/profile/wallpaper', {
    credentials: 'include',
  }), '壁纸加载失败'),
  uploadWallpaper: (file: Blob) => {
    const form = new FormData();
    form.append('file', file, 'wallpaper.webp');
    return parse<WallpaperResponse>(fetch('/api/profile/wallpaper', {
      method: 'POST',
      credentials: 'include',
      body: form,
    }), '壁纸上传失败');
  },
  deleteWallpaper: () => parse<WallpaperResponse>(fetch('/api/profile/wallpaper', {
    method: 'DELETE',
    credentials: 'include',
  }), '壁纸删除失败'),
};
