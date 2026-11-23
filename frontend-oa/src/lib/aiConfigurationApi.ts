import { request } from '@/lib/oaApi';
export const aiModelCategories = ['CHAT', 'EMBEDDING', 'RERANK', 'TTS', 'OCR', 'ASR', 'IMAGE', 'OTHER'] as const;
export type AiModelCategory = typeof aiModelCategories[number];
export interface AiConfiguredModel { id: string; contextWindow: number; maxOutputTokens: number; supportsImages: boolean; category?: AiModelCategory; sendDimensions?: boolean }
export interface AiConfiguration { baseUrl: string; model: string | null; keyConfigured: boolean; version: number; source: string; models: AiConfiguredModel[] }
export interface AiProviderProbe { baseUrl: string; apiKey?: string; model?: string; channelId?: number }
export interface AiChannel { id: number; name: string; configuration: AiConfiguration }
export const aiConfigurationApi = {
 channels: () => request<AiChannel[]>('/admin/ai-configuration/channels'),
 saveChannel: (id: number, name: string, configuration: { baseUrl: string; model: string | null; apiKey?: string; version: number; models: AiConfiguredModel[] }) => request<AiChannel>(`/admin/ai-configuration/channels/${id}`, { method: 'PUT', body: JSON.stringify({ name, configuration }) }),
 get: () => request<AiConfiguration>('/admin/ai-configuration'),
 save: (value: { baseUrl: string; model: string; apiKey?: string; version: number; models: AiConfiguredModel[] }) => request<AiConfiguration>('/admin/ai-configuration', { method: 'PUT', body: JSON.stringify(value) }),
 discover: (value: AiProviderProbe) => request<AiConfiguredModel[]>('/admin/ai-configuration/models', { method: 'POST', body: JSON.stringify(value) }),
 test: (value?: AiProviderProbe) => request<boolean>('/admin/ai-configuration/test', { method: 'POST', body: value ? JSON.stringify(value) : undefined }),
 catalog: (category: AiModelCategory = 'CHAT') => request<AiConfiguredModel[]>(category === 'CHAT' ? '/ai/models' : `/ai/models?category=${category}`),
};
