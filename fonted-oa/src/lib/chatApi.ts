import type { ChatAttachment, ChatConversation, ChatMessage, ChatStreamEvent } from '@/types/chat';
import { buildApiHeaders } from '@/lib/apiHeaders';
import i18n from '@/i18n';

const BASE = '/api';

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
  traceId?: string;
}

export class ChatApiError extends Error {
  constructor(message: string, readonly status: number, readonly errorCode?: string, readonly traceId?: string) {
    super(message);
    this.name = 'ChatApiError';
  }
}

function headers(json = true): HeadersInit {
  return buildApiHeaders(json);
}

async function parse<T>(response: Response): Promise<T> {
  const body = await response.json().catch(() => null) as ApiResult<T> | null;
  if (!response.ok || !body || body.code !== 200) {
    if (response.status === 401 && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('oa-auth-expired'));
    }
    throw new ChatApiError(body?.message || i18n.t('errors.requestFailed'), response.status, body?.errorCode, body?.traceId);
  }
  return body.data as T;
}

export async function listConversations(search = ''): Promise<ChatConversation[]> {
  const query = search ? `?search=${encodeURIComponent(search)}` : '';
  return parse(await fetch(`${BASE}/conversations${query}`, { headers: headers(false) }));
}

export async function createConversation(model: string): Promise<ChatConversation> {
  return parse(await fetch(`${BASE}/conversations`, {
    method: 'POST', headers: headers(), body: JSON.stringify({ model }),
  }));
}

export async function renameConversation(id: number, title: string): Promise<ChatConversation> {
  return parse(await fetch(`${BASE}/conversations/${id}`, {
    method: 'PATCH', headers: headers(), body: JSON.stringify({ title }),
  }));
}

export async function deleteConversation(id: number): Promise<void> {
  await parse(await fetch(`${BASE}/conversations/${id}`, { method: 'DELETE', headers: headers(false) }));
}

export async function listMessages(conversationId: number): Promise<ChatMessage[]> {
  return parse(await fetch(`${BASE}/conversations/${conversationId}/messages`, { headers: headers(false) }));
}

export async function uploadAttachment(
  conversationId: number,
  file: File,
  onProgress?: (percent: number) => void,
): Promise<ChatAttachment> {
  const form = new FormData();
  form.append('conversationId', String(conversationId));
  form.append('file', file);
  return new Promise<ChatAttachment>((resolve, reject) => {
    // fetch 不暴露上传进度，改用 XHR 上报 onprogress
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${BASE}/attachments`);
    xhr.withCredentials = true;
    const requestHeaders = buildApiHeaders(false);
    for (const [name, value] of Object.entries(requestHeaders)) {
      xhr.setRequestHeader(name, value);
    }
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.min(99, Math.round((event.loaded / event.total) * 100)));
      }
    };
    xhr.onload = () => {
      let body: ApiResult<ChatAttachment> | null = null;
      try {
        body = JSON.parse(xhr.responseText) as ApiResult<ChatAttachment>;
      } catch {
        body = null;
      }
      if (xhr.status === 401 && typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('oa-auth-expired'));
      }
      if (xhr.status >= 200 && xhr.status < 300 && body && body.code === 200) {
        resolve(body.data as ChatAttachment);
      } else {
        reject(new ChatApiError(body?.message || i18n.t('errors.requestFailed'), xhr.status, body?.errorCode, body?.traceId));
      }
    };
    xhr.onerror = () => reject(new ChatApiError(i18n.t('errors.chat.uploadFailed'), 0));
    xhr.ontimeout = () => reject(new ChatApiError(i18n.t('errors.requestFailed'), 0));
    xhr.send(form);
  });
}

export async function loadAttachmentContent(id: number, signal?: AbortSignal): Promise<string> {
  const response = await fetch(`${BASE}/attachments/${id}/content`, { headers: headers(false), signal });
  if (!response.ok) throw new ChatApiError(i18n.t('errors.chat.attachmentLoadFailed'), response.status);
  return URL.createObjectURL(await response.blob());
}

export async function loadAttachmentText(id: number, signal?: AbortSignal): Promise<string> {
  const response = await fetch(`${BASE}/attachments/${id}/content`, { headers: headers(false), signal });
  if (response.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('oa-auth-expired'));
  }
  if (!response.ok) throw new ChatApiError(i18n.t('errors.chat.markdownLoadFailed'), response.status);
  return response.text();
}

export async function updateMessageFeedback(messageId: number, feedback: 'like' | 'dislike' | 'none'): Promise<void> {
  await parse(await fetch(`${BASE}/conversations/messages/${messageId}/feedback`, {
    method: 'PATCH', headers: headers(), body: JSON.stringify({ feedback }),
  }));
}

interface ChatRequestPayload {
  conversationId: number;
  message: string;
  model: string;
  kbId?: number | null;
  attachmentIds: number[];
  maxContextRounds: number;
}

export async function streamChat(
  request: ChatRequestPayload,
  signal: AbortSignal,
  onEvent: (event: ChatStreamEvent) => void,
): Promise<void> {
  const response = await fetch(`${BASE}/chat/stream`, {
    method: 'POST', headers: headers(), body: JSON.stringify(request), signal,
  });
  if (!response.ok) await parse<never>(response);
  if (!response.body) throw new ChatApiError(i18n.t('errors.chat.streamUnavailable'), 500);

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true }).replaceAll('\r\n', '\n');
    const events = buffer.split('\n\n');
    buffer = events.pop() || '';
    events.forEach((event) => parseSseEvent(event, onEvent));
  }
  if (buffer.trim()) parseSseEvent(buffer, onEvent);
}

export async function sendChat(request: ChatRequestPayload, signal?: AbortSignal): Promise<string> {
  return parse(await fetch(`${BASE}/chat`, {
    method: 'POST', headers: headers(), body: JSON.stringify(request), signal,
  }));
}

function parseSseEvent(raw: string, onEvent: (event: ChatStreamEvent) => void): void {
  const data = raw.split('\n')
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  if (!data) return;
  const event = JSON.parse(data) as ChatStreamEvent;
  onEvent(event);
  if (event.type === 'error') throw new ChatApiError(event.data || i18n.t('errors.chat.aiServiceUnavailable'), 503, event.errorCode || undefined, event.traceId);
}
