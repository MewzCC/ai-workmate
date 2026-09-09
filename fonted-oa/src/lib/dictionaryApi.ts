import { queryString, request } from '@/lib/oaApi';

export type DictionaryStatus = 'ACTIVE' | 'DISABLED';

export interface DictionaryType {
  id: number;
  code: string;
  name: string;
  description?: string;
  status: DictionaryStatus;
  sortOrder: number;
  itemCount: number;
  activeItemCount: number;
  version: number;
  updatedAt: string;
  canManage: boolean;
}

export interface DictionaryItem {
  id: number;
  dictionaryTypeId: number;
  value: string;
  label: string;
  description?: string;
  status: DictionaryStatus;
  sortOrder: number;
  usageCount: number;
  version: number;
  updatedAt: string;
  canManage: boolean;
  canDelete: boolean;
}

export interface DictionaryTypePayload {
  code: string;
  name: string;
  description?: string;
  sortOrder?: number;
  version?: number;
}

export interface DictionaryItemPayload {
  value: string;
  label: string;
  description?: string;
  sortOrder?: number;
  version?: number;
}

export const dictionaryApi = {
  listTypes: (params: { keyword?: string; status?: string } = {}) =>
    request<{ records: DictionaryType[]; canManage: boolean }>(`/admin/dictionaries${queryString(params)}`),
  createType: (payload: DictionaryTypePayload) => request<DictionaryType>('/admin/dictionaries', {
    method: 'POST', body: JSON.stringify(payload),
  }),
  updateType: (id: number, payload: DictionaryTypePayload) => request<DictionaryType>(`/admin/dictionaries/${id}`, {
    method: 'PUT', body: JSON.stringify(payload),
  }),
  setTypeStatus: (id: number, status: DictionaryStatus, version: number) =>
    request<DictionaryType>(`/admin/dictionaries/${id}/status`, {
      method: 'PUT', body: JSON.stringify({ status, version }),
    }),
  deleteType: (id: number, version: number) => request<void>(`/admin/dictionaries/${id}`, {
    method: 'DELETE', body: JSON.stringify({ version }),
  }),
  listItems: (typeId: number, params: { keyword?: string; status?: string; page?: number; size?: number } = {}) =>
    request<{ records: DictionaryItem[]; total: number; page: number; size: number; canManage: boolean }>(
      `/admin/dictionaries/${typeId}/items${queryString(params)}`,
    ),
  createItem: (typeId: number, payload: DictionaryItemPayload) =>
    request<DictionaryItem>(`/admin/dictionaries/${typeId}/items`, { method: 'POST', body: JSON.stringify(payload) }),
  updateItem: (typeId: number, itemId: number, payload: DictionaryItemPayload) =>
    request<DictionaryItem>(`/admin/dictionaries/${typeId}/items/${itemId}`, {
      method: 'PUT', body: JSON.stringify(payload),
    }),
  setItemStatus: (typeId: number, itemId: number, status: DictionaryStatus, version: number) =>
    request<DictionaryItem>(`/admin/dictionaries/${typeId}/items/${itemId}/status`, {
      method: 'PUT', body: JSON.stringify({ status, version }),
    }),
  deleteItem: (typeId: number, itemId: number, version: number) =>
    request<void>(`/admin/dictionaries/${typeId}/items/${itemId}`, {
      method: 'DELETE', body: JSON.stringify({ version }),
    }),
  options: (code: string) => request<Array<{ value: string; label: string; sortOrder: number }>>(
    `/dictionaries/${encodeURIComponent(code)}/items`,
  ),
};
