'use client';

import { create } from 'zustand';
import { message as antMessage } from 'antd';
import {
  createConversation, deleteConversation, listConversations, listMessages,
  renameConversation, sendChat, streamChat, uploadAttachment,
} from '@/lib/chatApi';
import type { ChatAttachment, ChatConversation, ChatMessage, ChatSettings } from '@/types/chat';
import { DEFAULT_AI_MODEL, normalizeAiModel } from '@/config/aiModels';
import { StreamTypewriter } from '@/lib/StreamTypewriter';

const SETTINGS_KEY = 'workmeta-ai-chat-settings';
const controllers = new Map<number, AbortController>();
const typewriters = new Map<number, StreamTypewriter>();

interface AiChatState {
  conversations: ChatConversation[];
  activeId: number | null;
  messagesByConversation: Record<number, ChatMessage[]>;
  pendingAttachments: Record<number, ChatAttachment[]>;
  generatingIds: number[];
  loading: boolean;
  settings: ChatSettings;
  loadConversations: (search?: string) => Promise<void>;
  newConversation: () => Promise<number | null>;
  selectConversation: (id: number) => Promise<void>;
  rename: (id: number, title: string) => Promise<void>;
  remove: (id: number) => Promise<void>;
  upload: (files: File[]) => Promise<void>;
  removePendingAttachment: (id: number) => void;
  send: (content: string) => Promise<void>;
  stop: (id: number) => void;
  retry: (content: string) => Promise<void>;
  updateSettings: (settings: ChatSettings) => void;
  clearAll: () => Promise<void>;
}

const defaultSettings: ChatSettings = { model: DEFAULT_AI_MODEL, maxContextRounds: 10, stream: true };

function readSettings(): ChatSettings {
  if (typeof window === 'undefined') return defaultSettings;
  try {
    const stored = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}');
    const settings = { ...defaultSettings, ...stored, model: normalizeAiModel(stored.model) };
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    return settings;
  } catch {
    return defaultSettings;
  }
}

export const useAiChatStore = create<AiChatState>((set, get) => ({
  conversations: [],
  activeId: null,
  messagesByConversation: {},
  pendingAttachments: {},
  generatingIds: [],
  loading: false,
  settings: readSettings(),

  loadConversations: async (search = '') => {
    set({ loading: true });
    try {
      const conversations = await listConversations(search);
      set({ conversations, activeId: get().activeId ?? conversations[0]?.id ?? null });
    } finally {
      set({ loading: false });
    }
  },

  newConversation: async () => {
    try {
      const conversation = await createConversation(get().settings.model);
      set((state) => ({
        conversations: [conversation, ...state.conversations], activeId: conversation.id,
        messagesByConversation: { ...state.messagesByConversation, [conversation.id]: [] },
      }));
      return conversation.id;
    } catch (error) {
      antMessage.error(error instanceof Error ? error.message : '新建会话失败');
      return null;
    }
  },

  selectConversation: async (id) => {
    set({ activeId: id });
    if (get().messagesByConversation[id]) return;
    try {
      const messages = await listMessages(id);
      set((state) => ({ messagesByConversation: { ...state.messagesByConversation, [id]: messages } }));
    } catch (error) {
      antMessage.error(error instanceof Error ? error.message : '聊天记录加载失败');
    }
  },

  rename: async (id, title) => {
    const updated = await renameConversation(id, title);
    set((state) => ({ conversations: state.conversations.map((item) => item.id === id ? updated : item) }));
  },

  remove: async (id) => {
    await deleteConversation(id);
    controllers.get(id)?.abort();
    typewriters.get(id)?.cancel();
    set((state) => {
      const conversations = state.conversations.filter((item) => item.id !== id);
      const messages = { ...state.messagesByConversation };
      delete messages[id];
      return { conversations, messagesByConversation: messages, activeId: state.activeId === id ? conversations[0]?.id ?? null : state.activeId };
    });
  },

  upload: async (files) => {
    let conversationId = get().activeId;
    if (!conversationId) conversationId = await get().newConversation();
    if (!conversationId) return;
    for (const file of files) {
      try {
        const attachment = await uploadAttachment(conversationId, file);
        attachment.previewUrl = file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined;
        set((state) => ({ pendingAttachments: {
          ...state.pendingAttachments,
          [conversationId!]: [...(state.pendingAttachments[conversationId!] || []), attachment],
        } }));
      } catch (error) {
        antMessage.error(`${file.name}：${error instanceof Error ? error.message : '上传失败'}`);
      }
    }
  },

  removePendingAttachment: (attachmentId) => {
    const conversationId = get().activeId;
    if (!conversationId) return;
    set((state) => ({ pendingAttachments: {
      ...state.pendingAttachments,
      [conversationId]: (state.pendingAttachments[conversationId] || []).filter((item) => item.id !== attachmentId),
    } }));
  },

  send: async (rawContent) => {
    const state = get();
    const conversationId = state.activeId;
    if (!conversationId || controllers.has(conversationId)) return;
    const attachments = state.pendingAttachments[conversationId] || [];
    const content = rawContent.trim() || '请分析这些附件。';
    const now = new Date().toISOString();
    const userId = `local-user-${crypto.randomUUID()}`;
    const assistantId = `local-assistant-${crypto.randomUUID()}`;
    const user: ChatMessage = { id: userId, role: 'user', content, status: 'success', feedback: null, attachments, createdAt: now };
    const assistant: ChatMessage = { id: assistantId, role: 'assistant', content: '', status: 'sending', feedback: null, attachments: [], createdAt: now };
    appendMessages(set, conversationId, user, assistant);
    set((current) => ({ pendingAttachments: { ...current.pendingAttachments, [conversationId]: [] }, generatingIds: [...current.generatingIds, conversationId] }));

    const controller = new AbortController();
    controllers.set(conversationId, controller);
    try {
      const request = {
        conversationId,
        message: content,
        model: state.settings.model,
        attachmentIds: attachments.map((item) => item.id),
        maxContextRounds: state.settings.maxContextRounds,
      };
      if (state.settings.stream) {
        const typewriter = new StreamTypewriter((delta) => {
          appendMessageContent(set, conversationId, assistantId, delta);
        });
        typewriters.set(conversationId, typewriter);
        await streamChat(request, controller.signal, (event) => {
          if (event.type === 'delta' && event.data) typewriter.push(event.data);
        });
        await typewriter.finish();
      } else {
        await sendChat(request, controller.signal);
      }
      const persistedMessages = await listMessages(conversationId);
      set((current) => ({ messagesByConversation: { ...current.messagesByConversation, [conversationId]: persistedMessages } }));
      await get().loadConversations();
    } catch (error) {
      const typewriter = typewriters.get(conversationId);
      if (controller.signal.aborted) {
        typewriter?.cancel();
      } else {
        await typewriter?.finish();
      }
      if (!controller.signal.aborted) antMessage.error(error instanceof Error ? error.message : 'AI 回复失败');
      updateMessage(set, conversationId, assistantId, { status: 'failed' });
    } finally {
      controllers.delete(conversationId);
      typewriters.delete(conversationId);
      set((current) => ({ generatingIds: current.generatingIds.filter((id) => id !== conversationId) }));
    }
  },

  stop: (id) => {
    controllers.get(id)?.abort();
    typewriters.get(id)?.cancel();
  },
  retry: async (content) => get().send(content),

  updateSettings: (settings) => {
    localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
    set({ settings });
  },

  clearAll: async () => {
    await Promise.all(get().conversations.map((item) => deleteConversation(item.id)));
    controllers.forEach((controller) => controller.abort());
    typewriters.forEach((typewriter) => typewriter.cancel());
    controllers.clear();
    typewriters.clear();
    set({ conversations: [], activeId: null, messagesByConversation: {}, pendingAttachments: {}, generatingIds: [] });
  },
}));

function appendMessages(set: (value: Partial<AiChatState> | ((state: AiChatState) => Partial<AiChatState>)) => void,
                        conversationId: number, ...messages: ChatMessage[]) {
  set((state) => ({ messagesByConversation: {
    ...state.messagesByConversation,
    [conversationId]: [...(state.messagesByConversation[conversationId] || []), ...messages],
  } }));
}

function updateMessage(set: (value: Partial<AiChatState> | ((state: AiChatState) => Partial<AiChatState>)) => void,
                       conversationId: number, messageId: number | string, patch: Partial<ChatMessage>) {
  set((state) => ({ messagesByConversation: {
    ...state.messagesByConversation,
    [conversationId]: (state.messagesByConversation[conversationId] || []).map((item) => item.id === messageId ? { ...item, ...patch } : item),
  } }));
}

function appendMessageContent(set: Parameters<typeof updateMessage>[0], conversationId: number,
                              temporaryId: string, delta: string) {
  set((state) => ({ messagesByConversation: {
    ...state.messagesByConversation,
    [conversationId]: (state.messagesByConversation[conversationId] || []).map((item) => {
      return item.id === temporaryId ? { ...item, content: item.content + delta } : item;
    }),
  } }));
}
