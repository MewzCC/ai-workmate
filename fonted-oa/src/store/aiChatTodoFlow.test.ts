import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  createConversation: vi.fn(), listConversations: vi.fn(), listMessages: vi.fn(),
  appendAgentResult: vi.fn(), sendChat: vi.fn(), streamChat: vi.fn(),
  deleteConversation: vi.fn(), renameConversation: vi.fn(), uploadAttachment: vi.fn(),
  planAiTask: vi.fn(), issueAiTaskConfirmation: vi.fn(), executeAiTask: vi.fn(), detail: vi.fn(), cancel: vi.fn(),
  error: vi.fn(),
}));

vi.mock('@/lib/chatApi', () => ({
  createConversation: api.createConversation, listConversations: api.listConversations,
  listMessages: api.listMessages, appendAgentResult: api.appendAgentResult,
  sendChat: api.sendChat, streamChat: api.streamChat, deleteConversation: api.deleteConversation,
  renameConversation: api.renameConversation, uploadAttachment: api.uploadAttachment,
}));
vi.mock('@/lib/oaApi', () => ({
  planAiTask: api.planAiTask, issueAiTaskConfirmation: api.issueAiTaskConfirmation, executeAiTask: api.executeAiTask,
  agentTaskApi: { detail: api.detail, cancel: api.cancel },
}));
vi.mock('@/lib/antdMessage', () => ({ message: { error: api.error } }));

import { useAiChatStore } from './aiChatStore';

describe('AI Workspace governed OA operations', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAiChatStore.setState({
      conversations: [], activeId: null, draftMode: true,
      messagesByConversation: {}, previewByConversation: {}, pendingAttachments: {},
      uploading: {}, generatingIds: [], pendingPlans: {}, composerMode: 'operation', loading: false,
    });
    api.createConversation.mockResolvedValue({ id: 3, title: '新对话', model: 'deepseek-v4-flash', updatedAt: '', createdAt: '' });
    api.listConversations.mockResolvedValue([]);
    api.listMessages.mockResolvedValue([
      { id: 1, role: 'user', content: '查询本周会议室', status: 'success', attachments: [], citations: [] },
      { id: 2, role: 'assistant', content: '会议室查询完成。', status: 'success', sourceTaskNo: 'task-1', sourceToolCode: 'meeting.query', attachments: [], citations: [] },
    ]);
  });

  it('executes any server-validated L0 read plan and persists its concrete result', async () => {
    api.planAiTask.mockResolvedValue({ taskId: 'task-1', planVersion: 1, planHash: 'hash', riskLevel: 'L0',
      confirmationRequired: false, steps: [{ toolCode: 'meeting.query', arguments: { page: 1, size: 20 } }] });
    api.executeAiTask.mockResolvedValue({ taskId: 'task-1', status: 'QUEUED' });
    api.detail.mockResolvedValue({ taskId: 'task-1', status: 'SUCCEEDED', steps: [{ toolCode: 'meeting.query', status: 'SUCCEEDED' }] });
    api.appendAgentResult.mockResolvedValue('会议室查询完成。');

    await useAiChatStore.getState().send('查询本周会议室');

    expect(api.planAiTask).toHaveBeenCalledWith({ input: '查询本周会议室', pageId: 'ai-workspace' });
    expect(api.executeAiTask).toHaveBeenCalledWith('task-1', { planVersion: 1, planHash: 'hash' });
    expect(api.appendAgentResult).toHaveBeenCalledWith(3, 'task-1');
    expect(api.sendChat).not.toHaveBeenCalled();
    expect(api.streamChat).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().messagesByConversation[3][1].sourceTaskNo).toBe('task-1');
    expect(useAiChatStore.getState().generatingIds).toEqual([]);
  });

  it('holds a write plan for confirmation and sends the one-time credential only on approval', async () => {
    api.planAiTask.mockResolvedValue({ taskId: 'task-2', planVersion: 2, planHash: 'write-hash', riskLevel: 'L2',
      confirmationRequired: true, summary: '预订会议室', steps: [{ toolCode: 'meeting.book', arguments: { roomId: 9 } }] });

    await useAiChatStore.getState().send('预订会议室');

    expect(api.executeAiTask).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().pendingPlans[3]?.taskId).toBe('task-2');

    api.issueAiTaskConfirmation.mockResolvedValue({ token: 'one-time', expiresAt: '' });
    api.executeAiTask.mockResolvedValue({ taskId: 'task-2', status: 'QUEUED' });
    api.detail.mockResolvedValue({ taskId: 'task-2', status: 'SUCCEEDED', steps: [{ toolCode: 'meeting.book', status: 'SUCCEEDED' }] });
    api.appendAgentResult.mockResolvedValue('会议室预订完成。');
    await useAiChatStore.getState().confirmOperation(3);

    expect(api.issueAiTaskConfirmation).toHaveBeenCalledWith('task-2', { planVersion: 2, planHash: 'write-hash' });
    expect(api.executeAiTask).toHaveBeenCalledWith('task-2', {
      planVersion: 2, planHash: 'write-hash', confirmationToken: 'one-time',
    });
    expect(api.appendAgentResult).toHaveBeenCalledWith(3, 'task-2');
    expect(useAiChatStore.getState().pendingPlans[3]).toBeUndefined();
  });
});
