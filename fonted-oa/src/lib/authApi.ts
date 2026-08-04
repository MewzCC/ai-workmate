import i18n from '@/i18n';
import { buildApiHeaders } from '@/lib/apiHeaders';

export type CodeScene = 'register' | 'login' | 'reset_password';

export interface AuthUser {
  id: number;
  name: string;
  email: string;
  tenantId: number;
  role: string;
  roles: string[];
  avatarUrl?: string | null;
  permissions: string[];
  dataScopes: string[];
  permissionVersion: number;
}

export interface CaptchaData {
  captchaId: string;
  image: string;
  expiresIn: number;
}

interface CaptchaApiData {
  captchaId: string;
  image?: string;
  captchaImage?: string;
  expiresIn: number;
}

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
}

export class AuthApiError extends Error {
  constructor(message: string, readonly status: number, readonly errorCode?: string) {
    super(message);
    this.name = 'AuthApiError';
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const isJson = Boolean(init?.body);
  const headers = buildApiHeaders(isJson, init?.headers);
  let response: Response;
  try {
    response = await fetch(`/api/auth${path}`, {
      credentials: 'include',
      ...init,
      headers,
    });
  } catch {
    throw new AuthApiError(i18n.t('errors.auth.serviceUnavailable'), 0, 'AUTH_SERVICE_UNAVAILABLE');
  }
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (!response.ok || !result || result.code !== 200) {
    const fallback = response.status >= 500
      ? i18n.t('errors.auth.serverUnavailable')
      : i18n.t('errors.auth.requestFailed');
    throw new AuthApiError(result?.message || fallback, response.status, result?.errorCode);
  }
  return result.data as T;
}

export const authApi = {
  captcha: async (): Promise<CaptchaData> => {
    const data = await request<CaptchaApiData>('/captcha');
    const image = data.image ?? data.captchaImage;
    if (!image) {
      throw new AuthApiError(i18n.t('errors.auth.captchaInvalid'), 502, 'AUTH_CAPTCHA_INVALID_RESPONSE');
    }
    return { captchaId: data.captchaId, image, expiresIn: data.expiresIn };
  },
  sendEmailCode: (payload: { email: string; scene: CodeScene; captchaId: string; captchaCode: string }) =>
    request<void>('/email-code/send', { method: 'POST', body: JSON.stringify(payload) }),
  passwordLogin: (payload: { email: string; password: string; remember: boolean; captchaId?: string; captchaCode?: string }) =>
    request<AuthUser>('/login/password', { method: 'POST', body: JSON.stringify(payload) }),
  emailCodeLogin: (payload: { email: string; emailCode: string; remember: boolean }) =>
    request<AuthUser>('/login/email-code', { method: 'POST', body: JSON.stringify(payload) }),
  register: (payload: { name: string; email: string; emailCode: string; password: string; agreement: boolean; requestId: string }) =>
    request<AuthUser>('/register', { method: 'POST', body: JSON.stringify(payload) }),
  resetPassword: (payload: { email: string; emailCode: string; newPassword: string }) =>
    request<void>('/password/reset', { method: 'POST', body: JSON.stringify(payload) }),
  me: () => request<AuthUser>('/me'),
  logout: () => request<void>('/logout', { method: 'POST' }),
};
