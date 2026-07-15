import type {
  AiTaskExecuteRequest,
  AiTaskExecuteResponse,
  AiTaskPlanRequest,
  AiTaskPlanResponse,
} from '@/types/oa';

const BASE = '/api';

interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
  requestId?: string;
  traceId?: string;
}

export class OaApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly errorCode: string,
    readonly requestId?: string,
    readonly traceId?: string,
  ) {
    super(message);
    this.name = 'OaApiError';
  }

  get retryable(): boolean {
    return this.status === 429 || this.status >= 500;
  }
}

async function parseResult<T>(res: Response): Promise<T> {
  const json = await res.json().catch(() => null) as ApiResult<T> | null;
  if (!res.ok || !json || json.code !== 200 || json.data === null) {
    const status = res.status || 500;
    const error = new OaApiError(
      json?.message || statusMessage(status),
      status,
      json?.errorCode || statusErrorCode(status),
      json?.requestId,
      json?.traceId,
    );
    if (status === 401 && typeof window !== 'undefined') {
      window.localStorage.removeItem('token');
    }
    throw error;
  }
  return json.data;
}

function statusErrorCode(status: number): string {
  if (status === 401) return 'AUTH_REQUIRED';
  if (status === 403) return 'PERMISSION_DENIED';
  if (status === 409) return 'AI_TASK_CONFLICT';
  if (status === 429) return 'RATE_LIMITED';
  return 'SYSTEM_ERROR';
}

function statusMessage(status: number): string {
  if (status === 401) return '请先登录后再使用 AI 能力';
  if (status === 403) return '当前账号没有执行该操作的权限';
  if (status === 409) return '任务状态已变化，请重新生成计划';
  if (status === 429) return '请求过于频繁，请稍后重试';
  return '服务暂时不可用，请稍后重试';
}

function requestHeaders(): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Request-Id': crypto.randomUUID().replaceAll('-', ''),
  };
  const token = typeof window === 'undefined' ? null : window.localStorage.getItem('token');
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

export function formatOaApiError(error: unknown): string {
  if (!(error instanceof OaApiError)) return '请求失败，请稍后重试';
  const trace = error.traceId ? `（追踪号：${error.traceId}）` : '';
  return `${error.message}${trace}`;
}

export async function getSystemHealth(): Promise<{ status: string; service: string }> {
  const res = await fetch(`${BASE}/system/health`);
  return parseResult(res);
}

export async function planAiTask(request: AiTaskPlanRequest): Promise<AiTaskPlanResponse> {
  const res = await fetch(`${BASE}/ai/tasks/plan`, {
    method: 'POST',
    headers: requestHeaders(),
    body: JSON.stringify(request),
  });
  return parseResult(res);
}

export async function executeAiTask(request: AiTaskExecuteRequest): Promise<AiTaskExecuteResponse> {
  const res = await fetch(`${BASE}/ai/tasks/execute`, {
    method: 'POST',
    headers: requestHeaders(),
    body: JSON.stringify(request),
  });
  return parseResult(res);
}
