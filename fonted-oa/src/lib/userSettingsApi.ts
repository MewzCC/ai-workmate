import { buildApiHeaders } from '@/lib/apiHeaders';
import i18n from '@/i18n';

const BASE = '/api';

interface ApiResult<T> {
  code: number;
  message: string;
  data: T | null;
}

export interface OcrSettings {
  forcePdfOcr: boolean;
}

export interface ChatPreferences {
  model: 'deepseek-v4-flash' | 'deepseek-v4-pro';
  maxContextRounds: number;
  stream: boolean;
  forcePdfOcr: boolean;
  initialized: boolean;
}

async function parse<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null) as ApiResult<T> | null;
  if (!response.ok || !body || body.code !== 200) {
    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    throw new Error(body?.message || i18n.t('errors.requestFailed'));
  }
  return body.data as T;
}

export async function getOcrSettings(): Promise<OcrSettings> {
  return parse(await fetch(`${BASE}/settings/ocr`, { headers: buildApiHeaders(false) }));
}

export async function updateOcrSettings(forcePdfOcr: boolean): Promise<OcrSettings> {
  return parse(await fetch(`${BASE}/settings/ocr`, {
    method: 'PUT',
    headers: buildApiHeaders(),
    body: JSON.stringify({ forcePdfOcr }),
  }));
}

export async function getChatPreferences(): Promise<ChatPreferences> {
  return parse(await fetch(`${BASE}/settings/chat`, {
    credentials: 'include', headers: buildApiHeaders(false),
  }));
}

export async function updateChatPreferences(
  preferences: Omit<ChatPreferences, 'initialized'>,
): Promise<ChatPreferences> {
  return parse(await fetch(`${BASE}/settings/chat`, {
    method: 'PUT', credentials: 'include', headers: buildApiHeaders(),
    body: JSON.stringify(preferences),
  }));
}
