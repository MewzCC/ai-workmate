import { request } from '@/lib/oaApi';
export interface AiConfiguredModel { id: string; contextWindow: number; maxOutputTokens: number; supportsImages: boolean }
export interface AiConfiguration { baseUrl: string; model: string; keyConfigured: boolean; version: number; source: string; models: AiConfiguredModel[] }
export interface AiProviderProbe { baseUrl: string; apiKey?: string; model?: string }
export const aiConfigurationApi = {
 get: () => request<AiConfiguration>('/admin/ai-configuration'),
 save: (value: { baseUrl: string; model: string; apiKey?: string; version: number; models: AiConfiguredModel[] }) => request<AiConfiguration>('/admin/ai-configuration', { method: 'PUT', body: JSON.stringify(value) }),
 discover: (value: AiProviderProbe) => request<AiConfiguredModel[]>('/admin/ai-configuration/models', { method: 'POST', body: JSON.stringify(value) }),
 test: (value?: AiProviderProbe) => request<boolean>('/admin/ai-configuration/test', { method: 'POST', body: value ? JSON.stringify(value) : undefined }),
 catalog: () => request<AiConfiguredModel[]>('/ai/models'),
};
