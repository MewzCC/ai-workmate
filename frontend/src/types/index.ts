export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
}

export interface Conversation {
  id: number;
  title: string;
  model: string;
  updatedAt: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  role: string;
}

export interface ChatRequest {
  conversationId?: number;
  message: string;
  model?: string;
}

export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}
