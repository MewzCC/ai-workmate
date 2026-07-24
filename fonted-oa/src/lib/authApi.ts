export type CodeScene = 'register' | 'login' | 'reset_password';

export interface AuthUser {
  id: number;
  name: string;
  email: string;
  role: string;
  avatarUrl?: string | null;
  permissions: string[];
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
  let response: Response;
  try {
    response = await fetch(`/api/auth${path}`, {
      credentials: 'include',
      ...init,
      headers: init?.body ? { 'Content-Type': 'application/json', ...init.headers } : init?.headers,
    });
  } catch {
    throw new AuthApiError('无法连接认证服务，请确认 8080 后端已经启动', 0, 'AUTH_SERVICE_UNAVAILABLE');
  }
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (!response.ok || !result || result.code !== 200) {
    const fallback = response.status >= 500
      ? '认证服务暂不可用，请确认 8080 后端和 Redis 已启动'
      : '认证请求失败';
    throw new AuthApiError(result?.message || fallback, response.status, result?.errorCode);
  }
  return result.data as T;
}

export const authApi = {
  captcha: async (): Promise<CaptchaData> => {
    const data = await request<CaptchaApiData>('/captcha');
    const image = data.image ?? data.captchaImage;
    if (!image) {
      throw new AuthApiError('图形验证码数据不完整，请重新加载', 502, 'AUTH_CAPTCHA_INVALID_RESPONSE');
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
