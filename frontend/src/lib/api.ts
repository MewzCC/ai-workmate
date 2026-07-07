import { ChatRequest, LoginResponse, ApiResult, Conversation } from '@/types';

const BASE = '/api';

// ========== Auth ==========

export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const json: ApiResult<LoginResponse> = await res.json();
  if (json.code !== 200) throw new Error(json.message);
  localStorage.setItem('token', json.data.token);
  return json.data;
}

export async function register(username: string, password: string, email?: string): Promise<LoginResponse> {
  const res = await fetch(`${BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, email }),
  });
  const json: ApiResult<LoginResponse> = await res.json();
  if (json.code !== 200) throw new Error(json.message);
  localStorage.setItem('token', json.data.token);
  return json.data;
}

// ========== Chat ==========

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('token');
  return token ? { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' } : { 'Content-Type': 'application/json' };
}

/**
 * 流式对话 — 返回 ReadableStream，逐字接收 AI 回复
 */
export async function* chatStream(request: ChatRequest): AsyncGenerator<string> {
  const res = await fetch(`${BASE}/chat/stream`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(request),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({ message: '请求失败' }));
    throw new Error(err.message || `HTTP ${res.status}`);
  }

  const reader = res.body?.getReader();
  if (!reader) throw new Error('无法读取响应流');

  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // 解析 SSE data 行
    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      const trimmed = line.trim();
      if (trimmed.startsWith('data:')) {
        const data = trimmed.slice(5).trim();
        if (data && data !== '[DONE]') {
          yield data;
        }
      }
    }
  }
}

/**
 * 获取对话列表
 */
export async function getConversations(): Promise<Conversation[]> {
  const res = await fetch(`${BASE}/conversations`, {
    headers: authHeaders(),
  });
  if (res.status === 401) return [];
  const json: ApiResult<Conversation[]> = await res.json();
  return json.data || [];
}
