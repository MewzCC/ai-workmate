/** OpenAI-compatible endpoints verified against provider documentation. */
export const AI_PROVIDER_PRESETS = [
 { id: 'siliconflow', name: 'SiliconFlow', baseUrl: 'https://api.siliconflow.cn/v1' },
 { id: 'deepseek', name: 'DeepSeek', baseUrl: 'https://api.deepseek.com/v1' },
 { id: 'mimo', name: 'MiMo', baseUrl: 'https://api.xiaomimimo.com/v1' },
 { id: 'qwen', name: 'Qwen', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1' },
 { id: 'openai', name: 'OpenAI', baseUrl: 'https://api.openai.com/v1' },
 { id: 'openrouter', name: 'OpenRouter', baseUrl: 'https://openrouter.ai/api/v1' },
 { id: 'ollama', name: 'Ollama', baseUrl: 'http://localhost:11434/v1' },
] as const;
