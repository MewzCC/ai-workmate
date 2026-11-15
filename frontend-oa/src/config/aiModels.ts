export const AI_MODEL_OPTIONS = [
  { value: 'default', label: 'default', multimodal: false },
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash', multimodal: false },
  { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro', multimodal: false },
] as const;

export type AiModelId = string;

export const DEFAULT_AI_MODEL: AiModelId = 'default';

/** 模型是否支持直接接收图片 */
export function isMultimodalModel(model: AiModelId): boolean {
  return AI_MODEL_OPTIONS.find((option) => option.value === model)?.multimodal ?? false;
}

export function normalizeAiModel(model: unknown): AiModelId {
  if (typeof model === 'string' && /^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$/.test(model) && model !== 'deepseek-chat') return model;
  return DEFAULT_AI_MODEL;
}
