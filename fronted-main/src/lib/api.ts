import { ChatRequest, ApiResult, Conversation } from '@/types';

const BASE = '/api';

// ========== Chat ==========

function authHeaders(): HeadersInit {
  return { 'Content-Type': 'application/json' };
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

    const blocks = buffer.split(/\r?\n\r?\n/);
    buffer = blocks.pop() || '';

    for (const block of blocks) {
      const dataLine = block.split(/\r?\n/).find((line) => line.startsWith('data:'));
      if (!dataLine) continue;
      const raw = dataLine.slice(5).trim();
      const event = JSON.parse(raw) as ChatSseEvent;
      if (event.type === 'delta' && event.data) yield event.data;
      if (event.type === 'error') {
        const trace = event.traceId ? `（追踪号：${event.traceId}）` : '';
        throw new Error(`${event.data || 'AI 对话服务暂时不可用'}${trace}`);
      }
      if (event.type === 'done') return;
    }
  }
}

interface ChatSseEvent {
  type: 'delta' | 'error' | 'done';
  data: string | null;
  errorCode: string | null;
  requestId: string;
  traceId: string;
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
