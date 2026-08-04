import i18n from '@/i18n';
import { buildApiHeaders } from '@/lib/apiHeaders';

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
}

export interface NotificationItem {
  id: number;
  type: 'approval' | 'system' | 'alert' | 'todo' | string;
  title: string;
  content: string;
  bizType: string | null;
  bizId: number | null;
  read: boolean;
  createdAt: string;
}

export interface NotificationPage {
  records: NotificationItem[];
  total: number;
  page: number;
  size: number;
}

const BASE = '/api';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    credentials: 'include',
    headers: buildApiHeaders(Boolean(init?.body), init?.headers),
  });
  const body = (await response.json().catch(() => null)) as ApiResult<T> | null;
  if (!response.ok || !body || body.code !== 200) {
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    throw new Error(body?.message || i18n.t('errors.notification.requestFailed'));
  }
  return body.data as T;
}

export async function listNotifications(page = 1, size = 20): Promise<NotificationPage> {
  return request(`/notifications?page=${page}&size=${size}`);
}

export async function fetchUnreadCount(): Promise<number> {
  const result = await request<{ unread: number }>('/notifications/unread-count');
  return result.unread;
}

export async function markNotificationRead(id: number): Promise<void> {
  await request(`/notifications/${id}/read`, { method: 'PATCH' });
}

export async function markAllNotificationsRead(): Promise<number> {
  return request('/notifications/read-all', { method: 'PATCH' });
}
