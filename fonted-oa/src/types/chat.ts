import type { AiModelId } from '@/config/aiModels';

export type ChatRole = 'user' | 'assistant' | 'system';
export type MessageStatus = 'sending' | 'success' | 'failed';
export type MessageFeedback = 'like' | 'dislike' | null;

export interface ChatAttachment {
  id: number;
  messageId?: number | null;
  type: 'image' | 'file';
  name: string;
  size: number;
  mimeType: string;
  contentUrl: string;
  parsed: boolean;
  createdAt: string;
  previewUrl?: string;
}

export interface ChatMessage {
  id: number | string;
  role: ChatRole;
  content: string;
  status: MessageStatus;
  feedback: MessageFeedback;
  attachments: ChatAttachment[];
  createdAt: string;
}

export interface ChatConversation {
  id: number;
  title: string;
  model: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatSettings {
  model: AiModelId;
  maxContextRounds: number;
  stream: boolean;
}

export interface ChatStreamEvent {
  type: 'metadata' | 'delta' | 'done' | 'error';
  data?: string | null;
  errorCode?: string | null;
  messageId?: number | null;
  conversationId?: number | null;
  requestId?: string;
  traceId?: string;
}
