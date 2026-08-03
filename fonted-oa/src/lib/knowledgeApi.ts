interface ApiResult<T> {
  code: number;
  errorCode?: string;
  message: string;
  data: T | null;
}

export interface EmbeddingStatus {
  enabled: boolean;
  provider: string;
  model: string;
  dimension: number;
  /** 全局 rerank（重排）是否启用 */
  rerankEnabled: boolean;
  /** 全局 rerank 模型名；未启用时为 null */
  rerankModel: string | null;
}

export interface KnowledgeBase {
  id: number;
  name: string;
  icon: string;
  description: string | null;
  docCount: number;
  chunkCount: number;
  embeddingProvider: string;
  embeddingModel: string;
  rerankModel: string | null;
  chunkSize: number;
  chunkOverlap: number;
  denseTopK: number;
  sparseTopK: number;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeBaseCreatePayload {
  name: string;
  icon?: string;
  description?: string;
}

export interface KnowledgeBaseUpdatePayload {
  name?: string;
  icon?: string;
  description?: string;
  chunkSize?: number;
  chunkOverlap?: number;
  denseTopK?: number;
  sparseTopK?: number;
}

export interface KnowledgeDocument {
  id: number;
  filename: string;
  fileSize: number;
  fileType: string;
  chunkCount: number;
  status: string;
  embeddingProvider: string;
  embeddingModel: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgePageResponse {
  records: KnowledgeDocument[];
  total: number;
  page: number;
  size: number;
}

export interface KnowledgeChunk {
  vectorId: number;
  chunkIndex: number;
  content: string;
  charCount: number;
}

export interface KnowledgeDocumentDetail {
  id: number;
  filename: string;
  fileSize: number;
  fileType: string;
  chunkCount: number;
  status: string;
  embeddingProvider: string;
  embeddingModel: string;
  createdAt: string;
  updatedAt: string;
  chunks: KnowledgeChunk[];
}

export interface KnowledgeSearchItem {
  docId: number;
  chunkId: number;
  filename: string;
  chunkIndex: number;
  content: string;
  score: number;
  matchType: string;
}

export interface KnowledgeSearchResponse {
  provider: string;
  model: string;
  dimension: number;
  records: KnowledgeSearchItem[];
}

function resolveResult<T>(result: ApiResult<T> | null, ok: boolean, isVoid: boolean): T {
  if (!ok || !result || result.code !== 200 || (!isVoid && result.data === null)) {
    throw new Error(result?.message || '知识库请求失败');
  }
  return (result.data ?? null) as T;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const isFormData = typeof FormData !== 'undefined' && init?.body instanceof FormData;
  const response = await fetch(`/api/knowledge${path}`, {
    credentials: 'include',
    ...init,
    headers: isFormData
      ? init?.headers
      : { 'Content-Type': 'application/json', ...init?.headers },
  });
  const result = await response.json().catch(() => null) as ApiResult<T> | null;
  if (response.status === 401 && typeof window !== 'undefined') {
    window.dispatchEvent(new CustomEvent('oa-auth-expired'));
  }
  return resolveResult(result, response.ok, init?.method === 'DELETE');
}

/** 带上传进度的 multipart 请求：fetch 不暴露上传进度，改用 XHR 实现 */
function requestWithProgress<T>(
  path: string,
  form: FormData,
  onProgress?: (percent: number) => void,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `/api/knowledge${path}`);
    xhr.withCredentials = true;
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.min(99, Math.round((event.loaded / event.total) * 100)));
      }
    };
    xhr.onload = () => {
      let result: ApiResult<T> | null = null;
      try {
        result = JSON.parse(xhr.responseText) as ApiResult<T>;
      } catch {
        result = null;
      }
      if (xhr.status === 401 && typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('oa-auth-expired'));
      }
      try {
        resolve(resolveResult(result, xhr.status >= 200 && xhr.status < 300, false));
      } catch (error) {
        reject(error);
      }
    };
    xhr.onerror = () => reject(new Error('上传失败，请检查网络连接'));
    xhr.ontimeout = () => reject(new Error('上传超时，请重试'));
    xhr.send(form);
  });
}

export const knowledgeApi = {
  embeddingStatus: () => request<EmbeddingStatus>('/embedding-status'),

  listBases: () => request<KnowledgeBase[]>('/bases'),
  createBase: (payload: KnowledgeBaseCreatePayload) =>
    request<KnowledgeBase>('/bases', { method: 'POST', body: JSON.stringify(payload) }),
  getBase: (kbId: number) => request<KnowledgeBase>(`/bases/${kbId}`),
  updateBase: (kbId: number, payload: KnowledgeBaseUpdatePayload) =>
    request<KnowledgeBase>(`/bases/${kbId}`, {
      method: 'PUT',
      body: JSON.stringify(payload),
    }),
  deleteBase: (kbId: number) =>
    request<void>(`/bases/${kbId}`, { method: 'DELETE' }),

  list: (kbId: number, page = 1, size = 20) =>
    request<KnowledgePageResponse>(`/documents?kbId=${kbId}&page=${page}&size=${size}`),
  create: (payload: { kbId: number; filename: string; content: string }) =>
    request<KnowledgeDocument>('/documents', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  upload: (kbId: number, file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData();
    form.append('kbId', String(kbId));
    form.append('file', file);
    return requestWithProgress<KnowledgeDocument>(`/documents/upload?kbId=${kbId}`, form, onProgress);
  },
  reindex: (documentId: number) =>
    request<KnowledgeDocument>(`/documents/${documentId}/reindex`, { method: 'POST' }),
  getDocument: (documentId: number) =>
    request<KnowledgeDocumentDetail>(`/documents/${documentId}`),
  deleteChunk: (documentId: number, chunkId: number) =>
    request<void>(`/documents/${documentId}/chunks/${chunkId}`, { method: 'DELETE' }),
  batchDelete: (ids: number[]) =>
    request<number>('/documents/batch-delete', {
      method: 'POST',
      body: JSON.stringify({ ids }),
    }),
  batchReindex: (ids: number[]) =>
    request<KnowledgeDocument[]>('/documents/batch-reindex', {
      method: 'POST',
      body: JSON.stringify({ ids }),
    }),
  remove: (documentId: number) =>
    request<void>(`/documents/${documentId}`, { method: 'DELETE' }),
  search: (payload: { query: string; topK?: number; minScore?: number }) =>
    request<KnowledgeSearchResponse>('/search', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  searchInBase: (kbId: number, payload: { query: string; topK?: number; minScore?: number }) =>
    request<KnowledgeSearchResponse>(`/bases/${kbId}/search`, {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
};
