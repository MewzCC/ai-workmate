import { create } from 'zustand';
import { Message } from '@/types';
import { chatStream } from '@/lib/api';

interface ChatState {
  // 消息列表
  messages: Message[];
  // 是否正在加载（AI 回复中）
  isLoading: boolean;
  // 流式内容缓冲
  streamingContent: string;
  // 当前对话 ID
  conversationId?: number;
  // 错误信息
  error: string | null;

  // Actions
  sendMessage: (content: string) => Promise<void>;
  clearMessages: () => void;
  setConversationId: (id: number | undefined) => void;
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  isLoading: false,
  streamingContent: '',
  conversationId: undefined,
  error: null,

  sendMessage: async (content: string) => {
    const state = get();
    if (state.isLoading || !content.trim()) return;

    // 添加用户消息
    const userMsg: Message = {
      id: `user-${Date.now()}`,
      role: 'user',
      content,
      timestamp: Date.now(),
    };

    set({
      messages: [...state.messages, userMsg],
      isLoading: true,
      streamingContent: '',
      error: null,
    });

    try {
      const gen = chatStream({
        conversationId: state.conversationId,
        message: content,
        model: 'deepseek-chat',
      });

      let fullContent = '';

      for await (const chunk of gen) {
        fullContent += chunk;
        set({ streamingContent: fullContent });
      }

      // 添加 AI 回复
      const assistantMsg: Message = {
        id: `assistant-${Date.now()}`,
        role: 'assistant',
        content: fullContent,
        timestamp: Date.now(),
      };

      set((s) => ({
        messages: [...s.messages, assistantMsg],
        isLoading: false,
        streamingContent: '',
      }));
    } catch (err: any) {
      set({
        isLoading: false,
        streamingContent: '',
        error: err.message || '发送失败',
      });
    }
  },

  clearMessages: () => {
    set({
      messages: [],
      streamingContent: '',
      error: null,
      conversationId: undefined,
    });
  },

  setConversationId: (id) => {
    set({ conversationId: id });
  },
}));
