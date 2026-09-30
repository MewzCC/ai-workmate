'use client';

import { create } from 'zustand';
import { message as antMessage } from '@/lib/antdMessage';
import {
  appendAgentTodoResult, createConversation, deleteConversation, listConversations, listMessages,
  renameConversation, sendChat, streamChat, uploadAttachment,
} from '@/lib/chatApi';
import type { ChatAttachment, ChatConversation, ChatMessage, ChatMessageCitation, ChatSettings } from '@/types/chat';
import { DEFAULT_AI_MODEL, normalizeAiModel } from '@/config/aiModels';
import { StreamTypewriter } from '@/lib/StreamTypewriter';
import { uuid } from '@/lib/uuid';
import i18n from '@/i18n';
import { getChatPreferences, updateChatPreferences } from '@/lib/userSettingsApi';
import { agentTaskApi, executeAiTask, planAiTask } from '@/lib/oaApi';

const SETTINGS_KEY = 'workmeta-ai-chat-settings';
const SETTINGS_MIGRATED_KEY = 'workmeta-ai-chat-settings-migrated';
const controllers = new Map<number, AbortController>();
const typewriters = new Map<number, StreamTypewriter>();
const agentTasks = new Map<number, string>();
let settingsHydration: Promise<void> | null = null;

export interface UploadProgressItem {
  /** 唯一标识，用于进度更新与移除 */
  key: string;
  name: string;
  percent: number;
}

interface AiChatState {
  conversations: ChatConversation[];
  activeId: number | null;
  draftMode: boolean;
  messagesByConversation: Record<number, ChatMessage[]>;
  previewByConversation: Record<number, ChatMessage[]>;
  pendingAttachments: Record<number, ChatAttachment[]>;
  /** 每个会话正在上传的文件与实时进度（percent 0~99，完成后移除） */
  uploading: Record<number, UploadProgressItem[]>;
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
  hydrateSettings: () => Promise<void>;
  updateSettings: (settings: ChatSettings, forcePdfOcr?: boolean) => Promise<void>;
  clearAll: () => Promise<void>;
}

const defaultSettings: ChatSettings = { model: DEFAULT_AI_MODEL, kbId: null, maxContextRounds: 10, stream: true };

/** A narrow read-intent route. All planning, execution and ownership checks remain server-side. */
export function isMyTodoQuery(content: string): boolean {
  const normalized = content.trim().replace(/[？?。！!，,\s]/g, '');
  return /^(?:请|帮我|给我|能否|可以)?(?:查(?:一?下|询|看)?|看(?:一?下)?|搜索|列出|显示)?(?:我(?:的)?|本人)(?:待办|待处理(?:事项|任务)?)(?:有(?:哪(?:些|几条))?|列表|事项|任务|情况)?$/.test(normalized);
}

async function waitForReadTask(taskId: string, signal: AbortSignal): Promise<void> {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
    const detail = await agentTaskApi.detail(taskId);
    if (detail.status === 'SUCCEEDED' && detail.steps.length === 1
        && detail.steps[0].toolCode === 'todo.query' && detail.steps[0].status === 'SUCCEEDED') return;
    if (['FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED', 'PARTIALLY_SUCCEEDED'].includes(detail.status)) {
      throw new Error(i18n.t('chat.todoQueryFailed'));
    }
    await new Promise<void>((resolve) => {
      const onAbort = () => { window.clearTimeout(timer); resolve(); };
      const timer = window.setTimeout(() => { signal.removeEventListener('abort', onAbort); resolve(); }, 1000);
      signal.addEventListener('abort', onAbort, { once: true });
    });
  }
  throw new Error(i18n.t('chat.todoQueryTimeout'));
}

export const useAiChatStore = create<AiChatState>((set, get) => ({
  conversations: [],
  activeId: null,
  draftMode: false,
  messagesByConversation: {},
  previewByConversation: {},
  pendingAttachments: {},
  uploading: {},
  generatingIds: [],
  loading: false,
  settings: defaultSettings,

  hydrateSettings: async () => {
    if (settingsHydration) return settingsHydration;
    settingsHydration = (async () => {
      const server = await getChatPreferences();
      let resolved = server;
      if (typeof window !== 'undefined' && !server.initialized
          && localStorage.getItem(SETTINGS_MIGRATED_KEY) !== 'true') {
        try {
          const legacy = JSON.parse(localStorage.getItem(SETTINGS_KEY) || '{}') as Partial<ChatSettings>;
          if (localStorage.getItem(SETTINGS_KEY)) {
            resolved = await updateChatPreferences({
              model: normalizeAiModel(legacy.model),
              maxContextRounds: typeof legacy.maxContextRounds === 'number'
                ? Math.min(20, Math.max(1, legacy.maxContextRounds)) : defaultSettings.maxContextRounds,
              stream: typeof legacy.stream === 'boolean' ? legacy.stream : defaultSettings.stream,
              forcePdfOcr: server.forcePdfOcr,
            });
          }
        } catch {
          // 旧版本地值不可解析时直接使用服务端默认值。
        }
      }
      if (typeof window !== 'undefined') {
        localStorage.removeItem(SETTINGS_KEY);
        localStorage.setItem(SETTINGS_MIGRATED_KEY, 'true');
      }
      set((state) => ({ settings: {
        ...state.settings,
        model: normalizeAiModel(resolved.model),
        maxContextRounds: resolved.maxContextRounds,
        stream: resolved.stream,
      } }));
    })();
    try {
      await settingsHydration;
    } finally {
      settingsHydration = null;
    }
  },

  loadConversations: async (search = '') => {
    set({ loading: true });
    try {
      const conversations = await listConversations(search);
      // 不自动选中第一个会话：保持 activeId 不变，让用户主动点击后再加载内容
      set({ conversations });
      // 并发预加载每个会话的最近一条消息，用于侧栏预览（不影响完整消息列表的加载逻辑）
      preloadPreviews(conversations, set);
    } finally {
      set({ loading: false });
    }
  },

  // 草稿模式：不立即调 API，只清空 activeId 并进入草稿状态。
  // 真正的后端会话在首次发送消息时由 send() 内部按需创建。
  newConversation: async () => {
    set({ activeId: null, draftMode: true });
    return null;
  },

  selectConversation: async (id) => {
    set({ activeId: id, draftMode: false });
    if (get().messagesByConversation[id]) return;
    try {
      const messages = await listMessages(id);
      set((state) => ({ messagesByConversation: { ...state.messagesByConversation, [id]: messages } }));
    } catch (error) {
      antMessage.error(error instanceof Error ? error.message : i18n.t('chat.loadHistoryFailed'));
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
      const previews = { ...state.previewByConversation };
      delete previews[id];
      return {
        conversations,
        messagesByConversation: messages,
        previewByConversation: previews,
        activeId: state.activeId === id ? conversations[0]?.id ?? null : state.activeId,
        draftMode: state.activeId === id ? false : state.draftMode,
      };
    });
  },

  upload: async (files) => {
    let conversationId = get().activeId;
    // 草稿模式下上传文件：先真正创建后端会话，再上传附件
    if (!conversationId) {
      try {
        const conversation = await createConversation(get().settings.model);
        conversationId = conversation.id;
        set((state) => ({
          conversations: [conversation, ...state.conversations],
          activeId: conversation.id,
          draftMode: false,
          messagesByConversation: { ...state.messagesByConversation, [conversation.id]: [] },
        }));
      } catch (error) {
        antMessage.error(error instanceof Error ? error.message : i18n.t('chat.createConversationFailed'));
        return;
      }
    }
    const convId = conversationId;
    await Promise.all(files.map(async (file) => {
      const key = uuid();
      const item: UploadProgressItem = { key, name: file.name, percent: 0 };
      set((state) => ({ uploading: {
        ...state.uploading,
        [convId]: [...(state.uploading[convId] || []), item],
      } }));
      try {
        const attachment = await uploadAttachment(convId, file, (percent) => {
          set((state) => ({ uploading: {
            ...state.uploading,
            [convId]: (state.uploading[convId] || []).map((it) => it.key === key ? { ...it, percent } : it),
          } }));
        });
        attachment.previewUrl = file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined;
        set((state) => ({ pendingAttachments: {
          ...state.pendingAttachments,
          [convId]: [...(state.pendingAttachments[convId] || []), attachment],
        } }));
      } catch (error) {
        antMessage.error(`${file.name}：${error instanceof Error ? error.message : i18n.t('chat.uploadFailed')}`);
      } finally {
        set((state) => ({ uploading: {
          ...state.uploading,
          [convId]: (state.uploading[convId] || []).filter((it) => it.key !== key),
        } }));
      }
    }));
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
    let conversationId = state.activeId;
    // 草稿模式：首次发送时才真正创建后端会话
    if (!conversationId) {
      try {
        const conversation = await createConversation(state.settings.model);
        conversationId = conversation.id;
        set((current) => ({
          conversations: [conversation, ...current.conversations],
          activeId: conversation.id,
          draftMode: false,
          messagesByConversation: { ...current.messagesByConversation, [conversation.id]: [] },
        }));
      } catch (error) {
        antMessage.error(error instanceof Error ? error.message : i18n.t('chat.createConversationFailed'));
        return;
      }
    }
    if (controllers.has(conversationId)) return;
    const attachments = state.pendingAttachments[conversationId] || [];
    const content = rawContent.trim() || i18n.t('chat.defaultAttachmentPrompt');
    const now = new Date().toISOString();
    const userId = `local-user-${uuid()}`;
    const assistantId = `local-assistant-${uuid()}`;
    const user: ChatMessage = { id: userId, role: 'user', content, status: 'success', feedback: null, attachments, citations: [], createdAt: now };
    const assistant: ChatMessage = { id: assistantId, role: 'assistant', content: '', status: 'sending', feedback: null, attachments: [], citations: [], createdAt: now };
    appendMessages(set, conversationId, user, assistant);
    set((current) => ({ pendingAttachments: { ...current.pendingAttachments, [conversationId]: [] }, generatingIds: [...current.generatingIds, conversationId] }));

    const controller = new AbortController();
    controllers.set(conversationId, controller);
    try {
      if (attachments.length === 0 && isMyTodoQuery(content)) {
        const plan = await planAiTask({ input: content, pageId: 'ai-workspace' });
        if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
        const argumentsObject = plan.steps[0]?.arguments ?? {};
        if (plan.riskLevel !== 'L0' || plan.confirmationRequired || plan.steps.length !== 1
            || plan.steps[0].toolCode !== 'todo.query'
            || (argumentsObject.status != null && argumentsObject.status !== 'PENDING')
            || argumentsObject.from != null || argumentsObject.to != null
            || (argumentsObject.page != null && argumentsObject.page !== 1)) {
          throw new Error(i18n.t('chat.todoPlanUnavailable'));
        }
        agentTasks.set(conversationId, plan.taskId);
        await executeAiTask(plan.taskId, { planVersion: plan.planVersion, planHash: plan.planHash });
        await waitForReadTask(plan.taskId, controller.signal);
        if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
        await appendAgentTodoResult(conversationId, plan.taskId);
      } else {
        const request = {
          conversationId,
          message: content,
          model: state.settings.model,
          kbId: state.settings.kbId ?? null,
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
            if (event.type === 'references' && event.data) {
              try {
                const citations = JSON.parse(event.data) as ChatMessageCitation[];
                updateMessage(set, conversationId, assistantId, { citations });
              } catch {
                // 引用数据异常时忽略，不影响对话主流程
              }
            }
          });
          await typewriter.finish();
        } else {
          await sendChat(request, controller.signal);
        }
      }
      const persistedMessages = await listMessages(conversationId);
      set((current) => ({
        messagesByConversation: { ...current.messagesByConversation, [conversationId]: persistedMessages },
        // 同步更新预览：用最新消息列表的最后一条
        previewByConversation: { ...current.previewByConversation, [conversationId]: persistedMessages.slice(-1) },
      }));
      await get().loadConversations();
    } catch (error) {
      const typewriter = typewriters.get(conversationId);
      if (controller.signal.aborted) {
        typewriter?.cancel();
      } else {
        await typewriter?.finish();
      }
      if (!controller.signal.aborted) antMessage.error(error instanceof Error ? error.message : i18n.t('chat.aiReplyFailed'));
      updateMessage(set, conversationId, assistantId, {
        status: 'failed', content: error instanceof Error && !controller.signal.aborted ? error.message : i18n.t('chat.aiReplyFailed'),
      });
    } finally {
      controllers.delete(conversationId);
      typewriters.delete(conversationId);
      agentTasks.delete(conversationId);
      set((current) => ({ generatingIds: current.generatingIds.filter((id) => id !== conversationId) }));
    }
  },

  stop: (id) => {
    controllers.get(id)?.abort();
    typewriters.get(id)?.cancel();
    const taskId = agentTasks.get(id);
    if (taskId) void agentTaskApi.cancel(taskId).catch(() => undefined);
  },
  retry: async (content) => get().send(content),

  updateSettings: async (settings, forcePdfOcr) => {
    const previous = get().settings;
    set({ settings });
    const serverFieldsChanged = previous.model !== settings.model
      || previous.maxContextRounds !== settings.maxContextRounds
      || previous.stream !== settings.stream
      || forcePdfOcr !== undefined;
    if (!serverFieldsChanged) return;
    try {
      const current = forcePdfOcr === undefined ? await getChatPreferences() : null;
      await updateChatPreferences({
        model: settings.model,
        maxContextRounds: settings.maxContextRounds,
        stream: settings.stream,
        forcePdfOcr: forcePdfOcr ?? current?.forcePdfOcr ?? false,
      });
    } catch (error) {
      set({ settings: previous });
      throw error;
    }
  },

  clearAll: async () => {
    await Promise.all(get().conversations.map((item) => deleteConversation(item.id)));
    controllers.forEach((controller) => controller.abort());
    typewriters.forEach((typewriter) => typewriter.cancel());
    controllers.clear();
    typewriters.clear();
    set({ conversations: [], activeId: null, draftMode: false, messagesByConversation: {}, previewByConversation: {}, pendingAttachments: {}, uploading: {}, generatingIds: [] });
  },
}));

function appendMessages(set: (value: Partial<AiChatState> | ((state: AiChatState) => Partial<AiChatState>)) => void,
                        conversationId: number, ...messages: ChatMessage[]) {
  set((state) => ({ messagesByConversation: {
    ...state.messagesByConversation,
    [conversationId]: [...(state.messagesByConversation[conversationId] || []), ...messages],
  } }));
}

// 并发预加载每个会话的最近一条消息，用于侧栏预览。
// - 并发限制 6（浏览器对同一域名默认并发上限），避免打爆后端
// - 失败的会话不影响其他，预览保持空
// - 只缓存最后一条消息到 previewByConversation，不影响完整消息列表
async function preloadPreviews(
  conversations: ChatConversation[],
  set: (value: Partial<AiChatState> | ((state: AiChatState) => Partial<AiChatState>)) => void,
) {
  if (!conversations.length) return;
  const CONCURRENCY = 6;
  const queue = [...conversations];
  const worker = async () => {
    while (queue.length) {
      const conv = queue.shift();
      if (!conv) break;
      try {
        const messages = await listMessages(conv.id);
        const last = messages[messages.length - 1];
        if (last) {
          set((state) => ({ previewByConversation: { ...state.previewByConversation, [conv.id]: [last] } }));
        }
      } catch {
        // 预览加载失败忽略，不影响主流程
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(CONCURRENCY, conversations.length) }, worker));
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
