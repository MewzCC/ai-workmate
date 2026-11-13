'use client';

import { create } from 'zustand';
import { message as globalAntMessage } from '@/lib/antdMessage';
import {
  appendAgentResult, createConversation, deleteConversation, listConversations, listMessages, latestConversationAgentTask,
  renameConversation, sendChat, streamChat, uploadAttachment,
} from '@/lib/chatApi';
import type { ChatAttachment, ChatComposerMode, ChatConversation, ChatMessage, ChatMessageCitation, ChatSettings } from '@/types/chat';
import { DEFAULT_AI_MODEL, normalizeAiModel } from '@/config/aiModels';
import { StreamTypewriter } from '@/lib/StreamTypewriter';
import { uuid } from '@/lib/uuid';
import i18n from '@/i18n';
import { getChatPreferences, updateChatPreferences } from '@/lib/userSettingsApi';
import { agentTaskApi, classifyAiIntent, executeAiTask, issueAiTaskConfirmation, planAiTask } from '@/lib/oaApi';
import type { AgentTaskDetail, AiTaskPlanResponse } from '@/types/oa';
import { cancelAgentTask } from '@/lib/agentTaskCancellation';
import { acceptedAgentTask, AgentObservationPending, isAgentTaskTerminal, observeAgentTask, restoreAgentPlan } from '@/lib/agentOperationState';

const SETTINGS_KEY = 'workmeta-ai-chat-settings';
const SETTINGS_MIGRATED_KEY = 'workmeta-ai-chat-settings-migrated';

export interface UploadProgressItem {
  /** 唯一标识，用于进度更新与移除 */
  key: string;
  name: string;
  percent: number;
}

interface AiChatState {
  composerMode: ChatComposerMode;
  pendingPlans: Record<number, AiTaskPlanResponse>;
  operationTasks: Record<number, AgentTaskDetail>;
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
  setComposerMode: (mode: ChatComposerMode) => void;
  confirmOperation: (conversationId: number) => Promise<void>;
  discardOperation: (conversationId: number) => Promise<void>;
  resumeOperation: (conversationId: number) => Promise<void>;
  stop: (id: number) => void;
  retry: (content: string) => Promise<void>;
  hydrateSettings: () => Promise<void>;
  updateSettings: (settings: ChatSettings, forcePdfOcr?: boolean) => Promise<void>;
  clearAll: () => Promise<void>;
}

const defaultSettings: ChatSettings = { model: DEFAULT_AI_MODEL, kbId: null, maxContextRounds: 10, stream: true };

function createChatSession() {
  const controllers = new Map<number, AbortController>();
  const typewriters = new Map<number, StreamTypewriter>();
  const agentTasks = new Map<number, string>();
  const planningConversations = new Set<number>();
  const cancellingConversations = new Set<number>();
  let settingsHydration: Promise<void> | null = null;
  let active = true;
  const antMessage = {
    error: (...args: Parameters<typeof globalAntMessage.error>) => { if (active) return globalAntMessage.error(...args); },
    info: (...args: Parameters<typeof globalAntMessage.info>) => { if (active) return globalAntMessage.info(...args); },
    warning: (...args: Parameters<typeof globalAntMessage.warning>) => { if (active) return globalAntMessage.warning(...args); },
  };
  const ensureActive = () => { if (!active) throw new DOMException('Session changed', 'AbortError'); };
  const store = create<AiChatState>((rawSet, rawGet) => {
    const set: ChatStateWriter = Object.assign((value: Parameters<ChatStateWriter>[0]) => { if (active) rawSet(value); }, { isActive: () => active });
    const get = () => { ensureActive(); return rawGet(); };
    return {
  conversations: [],
  composerMode: 'auto',
  pendingPlans: {},
  operationTasks: {},
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
    if (!active) return;
    if (settingsHydration) return settingsHydration;
    settingsHydration = (async () => {
      const server = await getChatPreferences();
      if (!active) return;
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
      if (!active) return;
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
    if (!active) return;
    set({ loading: true });
    try {
      const conversations = await listConversations(search);
      // 不自动选中第一个会话：保持 activeId 不变，让用户主动点击后再加载内容
      set({ conversations });
      // 并发预加载每个会话的最近一条消息，用于侧栏预览（不影响完整消息列表的加载逻辑）
      if (active) void preloadPreviews(conversations, set, () => active);
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
    try {
      if (!get().messagesByConversation[id]) {
        const messages = await listMessages(id);
        if (!active) return;
        set((state) => ({ messagesByConversation: { ...state.messagesByConversation, [id]: messages } }));
      }
      await get().resumeOperation(id);
    } catch (error) {
      antMessage.error(error instanceof Error ? error.message : i18n.t('chat.loadHistoryFailed'));
    }
  },

  rename: async (id, title) => {
    ensureActive();
    const updated = await renameConversation(id, title);
    set((state) => ({ conversations: state.conversations.map((item) => item.id === id ? updated : item) }));
  },

  remove: async (id) => {
    ensureActive();
    await deleteConversation(id);
    controllers.get(id)?.abort();
    typewriters.get(id)?.cancel();
    set((state) => {
      const conversations = state.conversations.filter((item) => item.id !== id);
      const messages = { ...state.messagesByConversation };
      delete messages[id];
      const previews = { ...state.previewByConversation };
      delete previews[id];
      const pendingPlans = { ...state.pendingPlans };
      delete pendingPlans[id];
      const operationTasks = { ...state.operationTasks };
      delete operationTasks[id];
      return {
        conversations,
        messagesByConversation: messages,
        previewByConversation: previews,
        pendingPlans,
        operationTasks,
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
    ensureActive();
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
        if (!active) return;
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

  setComposerMode: (composerMode) => set({ composerMode }),

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
    if (get().pendingPlans[conversationId]) {
      antMessage.warning(i18n.t('chat.operationPending'));
      return;
    }
    const activeOperation = get().operationTasks[conversationId];
    if (activeOperation && !isAgentTaskTerminal(activeOperation.status)) {
      antMessage.warning(i18n.t('chat.operationStillRunning'));
      return;
    }
    const attachments = state.pendingAttachments[conversationId] || [];
    const content = rawContent.trim() || i18n.t('chat.defaultAttachmentPrompt');
    if (state.composerMode === 'operation' && attachments.length > 0) {
      antMessage.error(i18n.t('chat.operationAttachmentsUnsupported'));
      return;
    }
    const now = new Date().toISOString();
    const userId = `local-user-${uuid()}`;
    const assistantId = `local-assistant-${uuid()}`;
    const user: ChatMessage = { id: userId, role: 'user', content, status: 'success', feedback: null, attachments, citations: [], createdAt: now };
    const assistant: ChatMessage = { id: assistantId, role: 'assistant', content: '', status: 'sending', feedback: null, attachments: [], citations: [], createdAt: now };
    appendMessages(set, conversationId, user, assistant);
    set((current) => ({ pendingAttachments: { ...current.pendingAttachments, [conversationId]: [] }, generatingIds: [...current.generatingIds, conversationId] }));

    const controller = new AbortController();
    controllers.set(conversationId, controller);
    let classifying = false;
    try {
      let operationRequest = state.composerMode === 'operation';
      if (state.composerMode === 'auto' && attachments.length === 0) {
        classifying = true;
        const intent = await classifyAiIntent(content, controller.signal);
        if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
        classifying = false;
        operationRequest = intent === 'OPERATION';
      }
      if (operationRequest) {
        updateMessage(set, conversationId, assistantId, { retryable: false });
        planningConversations.add(conversationId);
        const plan = await planAiTask({ input: content, pageId: 'ai-workspace', conversationId });
        planningConversations.delete(conversationId);
        if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
        if (!plan.steps.length) throw new Error(i18n.t('chat.operationPlanUnavailable'));
        agentTasks.set(conversationId, plan.taskId);
        set((current) => ({ pendingPlans: { ...current.pendingPlans, [conversationId]: plan } }));
        if (plan.confirmationRequired) {
          updateMessage(set, conversationId, assistantId, { content: plan.summary, status: 'success' });
          set((current) => ({ pendingPlans: { ...current.pendingPlans, [conversationId]: plan } }));
          return;
        }
        const accepted = await executeAiTask(plan.taskId, { planVersion: plan.planVersion, planHash: plan.planHash });
        rememberOperation(set, conversationId, acceptedAgentTask(plan, accepted));
        await observeAgentTask(plan.taskId, controller.signal, (detail) => set((current) => ({
          operationTasks: { ...current.operationTasks, [conversationId]: detail },
        })));
        if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
        await appendAgentResult(conversationId, plan.taskId);
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
      const reason = error instanceof Error ? error.message : i18n.t('chat.aiReplyFailed');
      const knownTaskId = agentTasks.get(conversationId);
      if (knownTaskId && !controller.signal.aborted) await reconcileKnownOperation(set, conversationId, knownTaskId);
      const failure = classifying ? i18n.t('chat.autoRoutingFailed', { reason }) : reason;
      if (error instanceof AgentObservationPending) antMessage.info(failure);
      else if (!controller.signal.aborted) antMessage.error(failure);
      updateMessage(set, conversationId, assistantId, {
        status: error instanceof AgentObservationPending ? 'success' : 'failed',
        content: !controller.signal.aborted ? failure : i18n.t('chat.aiReplyFailed'),
      });
    } finally {
      planningConversations.delete(conversationId);
      controllers.delete(conversationId);
      typewriters.delete(conversationId);
      agentTasks.delete(conversationId);
      set((current) => ({ generatingIds: current.generatingIds.filter((id) => id !== conversationId) }));
    }
  },

  confirmOperation: async (conversationId) => {
    const plan = get().pendingPlans[conversationId];
    if (!plan || controllers.has(conversationId)) return;
    const controller = new AbortController();
    controllers.set(conversationId, controller);
    agentTasks.set(conversationId, plan.taskId);
    set((current) => ({ generatingIds: [...current.generatingIds, conversationId] }));
    try {
      const credential = plan.confirmationRequired ? await issueAiTaskConfirmation(plan.taskId, {
        planVersion: plan.planVersion, planHash: plan.planHash,
      }) : null;
      if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
      const accepted = await executeAiTask(plan.taskId, {
        planVersion: plan.planVersion,
        planHash: plan.planHash,
        ...(credential ? { confirmationToken: credential.confirmationToken } : {}),
      });
      rememberOperation(set, conversationId, acceptedAgentTask(plan, accepted));
      const detail = await observeAgentTask(plan.taskId, controller.signal, (next) => rememberOperation(set, conversationId, next));
      if (controller.signal.aborted) throw new DOMException('Aborted', 'AbortError');
      await persistOperationResult(set, conversationId, detail);
      await get().loadConversations();
    } catch (error) {
      if (!controller.signal.aborted) {
        await reconcileKnownOperation(set, conversationId, plan.taskId);
        if (error instanceof AgentObservationPending) antMessage.info(error.message);
        else antMessage.error(error instanceof Error ? error.message : i18n.t('chat.operationFailed'));
      }
    } finally {
      controllers.delete(conversationId);
      agentTasks.delete(conversationId);
      set((current) => ({ generatingIds: current.generatingIds.filter((id) => id !== conversationId) }));
    }
  },

  discardOperation: async (conversationId) => {
    if (cancellingConversations.has(conversationId)) return;
    const taskId = get().pendingPlans[conversationId]?.taskId ?? get().operationTasks[conversationId]?.taskId ?? agentTasks.get(conversationId);
    if (!taskId) return;
    cancellingConversations.add(conversationId);
    try {
      const outcome = await cancelAgentTask(taskId);
      rememberOperation(set, conversationId, outcome.detail);
      if (isAgentTaskTerminal(outcome.detail.status)) {
        controllers.get(conversationId)?.abort();
        await persistOperationResult(set, conversationId, outcome.detail);
        await get().loadConversations();
      }
      if (!outcome.cancelled) antMessage.warning(i18n.t('chat.operationCancelNotApplied'));
    } catch (error) {
      antMessage.error(error instanceof Error ? error.message : i18n.t('oa.ai.cancelUnconfirmed'));
    } finally {
      cancellingConversations.delete(conversationId);
    }
  },

  resumeOperation: async (conversationId) => {
    if (controllers.has(conversationId)) return;
    const controller = new AbortController();
    controllers.set(conversationId, controller);
    set((current) => ({ generatingIds: [...new Set([...current.generatingIds, conversationId])] }));
    try {
      let detail = await latestConversationAgentTask(conversationId);
      const known = get().operationTasks[conversationId]?.taskId ?? get().pendingPlans[conversationId]?.taskId;
      if (!detail && known) detail = await agentTaskApi.detail(known);
      if (!detail || controller.signal.aborted) return;
      rememberOperation(set, conversationId, detail);
      if (['PLAN_READY', 'WAITING_CONFIRMATION'].includes(detail.status)) {
        const plan = restoreAgentPlan(detail);
        set((current) => ({ pendingPlans: { ...current.pendingPlans, [conversationId]: plan } }));
        return;
      }
      if (!isAgentTaskTerminal(detail.status)) {
        agentTasks.set(conversationId, detail.taskId);
        detail = await observeAgentTask(detail.taskId, controller.signal, (next) => rememberOperation(set, conversationId, next));
      }
      if (controller.signal.aborted) return;
      const taskId = detail.taskId;
      if (!get().messagesByConversation[conversationId]?.some((message) => message.sourceTaskNo === taskId))
        await persistOperationResult(set, conversationId, detail);
    } catch (error) {
      if (!controller.signal.aborted) {
        if (error instanceof AgentObservationPending) antMessage.info(error.message);
        else antMessage.error(error instanceof Error ? error.message : i18n.t('chat.operationFailed'));
      }
    } finally {
      if (controllers.get(conversationId) === controller) controllers.delete(conversationId);
      agentTasks.delete(conversationId);
      set((current) => ({ generatingIds: current.generatingIds.filter((id) => id !== conversationId) }));
    }
  },

  stop: (id) => {
    if (planningConversations.has(id)) {
      antMessage.info(i18n.t('chat.operationPlanningWait'));
      return;
    }
    if (agentTasks.has(id)) {
      void get().discardOperation(id);
      return;
    }
    controllers.get(id)?.abort(); typewriters.get(id)?.cancel();
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
      ensureActive();
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
    // Keep local state aligned with each completed deletion if a later conversation is protected by an active task.
    for (const conversation of [...get().conversations]) await get().remove(conversation.id);
    controllers.forEach((controller) => controller.abort());
    typewriters.forEach((typewriter) => typewriter.cancel());
    controllers.clear();
    typewriters.clear();
    set({ conversations: [], activeId: null, draftMode: false, messagesByConversation: {}, previewByConversation: {}, pendingAttachments: {}, uploading: {}, generatingIds: [], pendingPlans: {}, operationTasks: {} });
  },
    };
  });
  return { store, dispose: () => {
    const snapshot = store.getState();
    active = false;
    controllers.forEach(controller => controller.abort());
    typewriters.forEach(typewriter => typewriter.cancel());
    controllers.clear(); typewriters.clear(); agentTasks.clear(); planningConversations.clear(); cancellingConversations.clear();
    for (const attachment of [...Object.values(snapshot.pendingAttachments).flat(),
      ...Object.values(snapshot.messagesByConversation).flat().flatMap(message => message.attachments)]) {
      if (attachment.previewUrl) URL.revokeObjectURL(attachment.previewUrl);
    }
  } };
}

const useChatIdentity = create(() => ({ key: null as string | null, session: createChatSession() }));
function useScopedChat<T = AiChatState>(selector?: (state: AiChatState) => T): T {
  const session = useChatIdentity(state => state.session);
  return session.store(selector ?? ((state: AiChatState) => state as unknown as T));
}
/** Keep existing consumers while isolating asynchronous callbacks and caches per authenticated identity. */
export const useAiChatStore = Object.assign(useScopedChat, {
  getState: () => useChatIdentity.getState().session.store.getState(),
  getInitialState: () => useChatIdentity.getState().session.store.getInitialState(),
  setState: useChatIdentity.getState().session.store.setState,
});

export function bindChatIdentity(key: string | null): void {
  const current = useChatIdentity.getState();
  if (current.key === key) return;
  current.session.dispose();
  const session = createChatSession();
  useAiChatStore.setState = session.store.setState;
  useChatIdentity.setState({ key, session });
}

type ChatStateWriter = ((value: Partial<AiChatState> | ((state: AiChatState) => Partial<AiChatState>)) => void) & { isActive?: () => boolean };

async function reconcileKnownOperation(set: ChatStateWriter, conversationId: number, taskId: string) {
  if (set.isActive?.() === false) return;
  try {
    const detail = await agentTaskApi.detail(taskId);
    if (set.isActive?.() === false) return;
    if (detail.taskId !== taskId) return;
    rememberOperation(set, conversationId, detail);
    if (['PLAN_READY', 'WAITING_CONFIRMATION'].includes(detail.status)) {
      const plan = restoreAgentPlan(detail);
      set((current) => ({ pendingPlans: { ...current.pendingPlans, [conversationId]: plan } }));
    } else if (isAgentTaskTerminal(detail.status)) await persistOperationResult(set, conversationId, detail);
  } catch {
    // Retain the last acknowledged state. The caller reports the failure; recovery never repeats a write.
  }
}

function rememberOperation(set: ChatStateWriter, conversationId: number, detail: AgentTaskDetail) {
  set((current) => {
    const pendingPlans = { ...current.pendingPlans };
    if (!['PLAN_READY', 'WAITING_CONFIRMATION'].includes(detail.status)) delete pendingPlans[conversationId];
    return { pendingPlans, operationTasks: { ...current.operationTasks, [conversationId]: detail } };
  });
}

async function persistOperationResult(set: ChatStateWriter, conversationId: number, detail: AgentTaskDetail) {
  if (!isAgentTaskTerminal(detail.status) || set.isActive?.() === false) return;
  rememberOperation(set, conversationId, detail);
  await appendAgentResult(conversationId, detail.taskId);
  if (set.isActive?.() === false) return;
  const messages = await listMessages(conversationId);
  set((current) => ({ messagesByConversation: { ...current.messagesByConversation, [conversationId]: messages },
    previewByConversation: { ...current.previewByConversation, [conversationId]: messages.slice(-1) } }));
}

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
  isActive: () => boolean = () => true,
) {
  if (!conversations.length) return;
  const CONCURRENCY = 6;
  const queue = [...conversations];
  const worker = async () => {
    while (queue.length && isActive()) {
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
