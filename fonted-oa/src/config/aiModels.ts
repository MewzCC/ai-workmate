export const AI_MODEL_OPTIONS = [
  { value: 'deepseek-v4-flash', label: 'DeepSeek V4 Flash' },
  { value: 'deepseek-v4-pro', label: 'DeepSeek V4 Pro' },
] as const;

export type AiModelId = typeof AI_MODEL_OPTIONS[number]['value'];

export const DEFAULT_AI_MODEL: AiModelId = 'deepseek-v4-flash';

export function normalizeAiModel(model: unknown): AiModelId {
  if (model === 'deepseek-v4-pro') return model;
  return DEFAULT_AI_MODEL;
}
