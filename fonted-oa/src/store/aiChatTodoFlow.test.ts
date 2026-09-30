import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  createConversation: vi.fn(), listConversations: vi.fn(), listMessages: vi.fn(),
  appendAgentTodoResult: vi.fn(), sendChat: vi.fn(), streamChat: vi.fn(),
  deleteConversation: vi.fn(), renameConversation: vi.fn(), uploadAttachment: vi.fn(),
  planAiTask: vi.fn(), executeAiTask: vi.fn(), detail: vi.fn(), cancel: vi.fn(),
  error: vi.fn(),
}));

vi.mock('@/lib/chatApi', () => ({
  createConversation: api.createConversation, listConversations: api.listConversations,
  listMessages: api.listMessages, appendAgentTodoResult: api.appendAgentTodoResult,
  sendChat: api.sendChat, streamChat: api.streamChat, deleteConversation: api.deleteConversation,
  renameConversation: api.renameConversation, uploadAttachment: api.uploadAttachment,
}));
vi.mock('@/lib/oaApi', () => ({
  planAiTask: api.planAiTask, executeAiTask: api.executeAiTask,
  agentTaskApi: { detail: api.detail, cancel: api.cancel },
}));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error } }));

import { useAiChatStore } from './aiChatStore';

describe('AI Workspace controlled todo read', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAiChatStore.setState({
      conversations: [], activeId: null, draftMode: true,
      messagesByConversation: {}, previewByConversation: {}, pendingAttachments: {},
      uploading: {}, generatingIds: [], loading: false,
    });
    api.createConversation.mockResolvedValue({ id: 3, title: '新对话', model: 'deepseek-v4-flash', updatedAt: '', createdAt: '' });
    api.listConversations.mockResolvedValue([]);
    api.listMessages.mockResolvedValue([
      { id: 1, role: 'user', content: '查一下我的待办', status: 'success', attachments: [], citations: [] },
      { id: 2, role: 'assistant', content: '本次待办查询没有匹配的事项。', status: 'success', sourceTaskNo: 'task-1', attachments: [], citations: [] },
    ]);
  });

  it('uses only a validated L0 gateway plan and persists the server result', async () => {
    api.planAiTask.mockResolvedValue({ taskId: 'task-1', planVersion: 1, planHash: 'hash', riskLevel: 'L0',
      confirmationRequired: false, steps: [{ toolCode: 'todo.query', arguments: { page: 1, size: 20 } }] });
    api.executeAiTask.mockResolvedValue({ taskId: 'task-1', status: 'QUEUED' });
    api.detail.mockResolvedValue({ taskId: 'task-1', status: 'SUCCEEDED', steps: [{ toolCode: 'todo.query', status: 'SUCCEEDED' }] });
    api.appendAgentTodoResult.mockResolvedValue('本次待办查询没有匹配的事项。');

    await useAiChatStore.getState().send('查一下我的待办');

    expect(api.planAiTask).toHaveBeenCalledWith({ input: '查一下我的待办', pageId: 'ai-workspace' });
    expect(api.executeAiTask).toHaveBeenCalledWith('task-1', { planVersion: 1, planHash: 'hash' });
    expect(api.appendAgentTodoResult).toHaveBeenCalledWith(3, 'task-1');
    expect(api.sendChat).not.toHaveBeenCalled();
    expect(api.streamChat).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().messagesByConversation[3][1].sourceTaskNo).toBe('task-1');
    expect(useAiChatStore.getState().generatingIds).toEqual([]);
  });

  it('refuses a planner response that changes the query scope', async () => {
    api.planAiTask.mockResolvedValue({ taskId: 'task-2', planVersion: 1, planHash: 'hash', riskLevel: 'L0',
      confirmationRequired: false, steps: [{ toolCode: 'todo.query', arguments: { status: 'APPROVED' } }] });

    await useAiChatStore.getState().send('查一下我的待办');

    expect(api.executeAiTask).not.toHaveBeenCalled();
    expect(api.appendAgentTodoResult).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().messagesByConversation[3][1].status).toBe('failed');
  });
});
