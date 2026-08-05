export const AI_MODEL_OPTIONS = [
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash', multimodal: false },
  { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro', multimodal: false },
] as const;

export type AiModelId = typeof AI_MODEL_OPTIONS[number]['value'];

export const DEFAULT_AI_MODEL: AiModelId = 'deepseek-v4-flash';

/** 模型是否支持直接接收图片 */
export function isMultimodalModel(model: AiModelId): boolean {
  return AI_MODEL_OPTIONS.find((option) => option.value === model)?.multimodal ?? false;
}

export function normalizeAiModel(model: unknown): AiModelId {
  if (model === 'deepseek-v4-pro') return model;
  return DEFAULT_AI_MODEL;
}
