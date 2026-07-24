import type { AuthUser } from './authApi';

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
}

async function parse(responsePromise: Promise<Response>): Promise<AuthUser> {
  const response = await responsePromise;
  const result = await response.json().catch(() => null) as ApiResult<AuthUser> | null;
  if (!response.ok || !result || result.code !== 200 || !result.data) {
    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    throw new Error(result?.message || '用户资料更新失败');
  }
  return result.data;
}

export const profileApi = {
  update: (name: string) => parse(fetch('/api/profile', {
    method: 'PUT',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name }),
  })),
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return parse(fetch('/api/profile/avatar', {
      method: 'POST',
      credentials: 'include',
      body: form,
    }));
  },
  deleteAvatar: () => parse(fetch('/api/profile/avatar', {
    method: 'DELETE',
    credentials: 'include',
  })),
};
