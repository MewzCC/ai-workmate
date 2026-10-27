import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({
  createConversation: vi.fn(), listConversations: vi.fn(), listMessages: vi.fn(),
  appendAgentResult: vi.fn(), sendChat: vi.fn(), streamChat: vi.fn(),
  deleteConversation: vi.fn(), renameConversation: vi.fn(), uploadAttachment: vi.fn(),
  planAiTask: vi.fn(), issueAiTaskConfirmation: vi.fn(), executeAiTask: vi.fn(), detail: vi.fn(), cancel: vi.fn(),
  classifyAiIntent: vi.fn(),
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
  classifyAiIntent: api.classifyAiIntent,
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
    api.classifyAiIntent.mockResolvedValue('OPERATION');
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
    expect(api.classifyAiIntent).not.toHaveBeenCalled();
    expect(api.executeAiTask).toHaveBeenCalledWith('task-1', { planVersion: 1, planHash: 'hash' });
    expect(api.appendAgentResult).toHaveBeenCalledWith(3, 'task-1');
    expect(api.sendChat).not.toHaveBeenCalled();
    expect(api.streamChat).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().messagesByConversation[3][1].sourceTaskNo).toBe('task-1');
    expect(useAiChatStore.getState().generatingIds).toEqual([]);
  });

  it('holds a write plan for confirmation and sends the one-time credential only on approval', async () => {
    useAiChatStore.setState({ composerMode: 'auto' });
    api.planAiTask.mockResolvedValue({ taskId: 'task-2', planVersion: 2, planHash: 'write-hash', riskLevel: 'L2',
      confirmationRequired: true, summary: '预订会议室', steps: [{ toolCode: 'meeting.book', arguments: { roomId: 9 } }] });

    await useAiChatStore.getState().send('预订会议室');

    expect(api.classifyAiIntent).toHaveBeenCalledWith('预订会议室', expect.any(AbortSignal));
    expect(api.executeAiTask).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().pendingPlans[3]?.taskId).toBe('task-2');

    api.issueAiTaskConfirmation.mockResolvedValue({ confirmationToken: 'one-time', expiresAt: '' });
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

  it.each([
    ['查询本周会议室', 'meeting.query'], ['查看本月预算', 'budget.query'], ['查看平台失败率', 'observability.overview.query'],
  ])('routes %s through the same server intent and governed task flow', async (input, toolCode) => {
    useAiChatStore.setState({ composerMode: 'auto' });
    api.planAiTask.mockResolvedValue({ taskId: 'task-1', planVersion: 1, planHash: 'hash', riskLevel: 'L0',
      confirmationRequired: false, steps: [{ toolCode, arguments: {} }] });
    api.executeAiTask.mockResolvedValue({ taskId: 'task-1', status: 'QUEUED' });
    api.detail.mockResolvedValue({ taskId: 'task-1', status: 'SUCCEEDED', steps: [{ toolCode, status: 'SUCCEEDED' }] });
    api.appendAgentResult.mockResolvedValue('真实业务结果');
    api.listMessages.mockResolvedValue([
      { id: 1, role: 'user', content: input, status: 'success', attachments: [], citations: [] },
      { id: 2, role: 'assistant', content: '真实业务结果', status: 'success', sourceTaskNo: 'task-1', sourceToolCode: toolCode, attachments: [], citations: [] },
    ]);
    await useAiChatStore.getState().send(input);
    expect(api.classifyAiIntent).toHaveBeenCalledWith(input, expect.any(AbortSignal));
    expect(api.planAiTask).toHaveBeenCalledWith({ input, pageId: 'ai-workspace' });
    expect(api.appendAgentResult).toHaveBeenCalledWith(3, 'task-1');
    expect(useAiChatStore.getState().messagesByConversation[3][1].content).toBe('真实业务结果');
    expect(api.sendChat).not.toHaveBeenCalled();
    expect(api.streamChat).not.toHaveBeenCalled();
  });

  it('preserves normal streaming chat when the server classifies the input as chat', async () => {
    useAiChatStore.setState({ composerMode: 'auto' });
    api.classifyAiIntent.mockResolvedValue('CHAT');
    api.streamChat.mockResolvedValue(undefined);
    await useAiChatStore.getState().send('解释一下会议室预约的常见规则');
    expect(api.classifyAiIntent).toHaveBeenCalledOnce();
    expect(api.streamChat).toHaveBeenCalledOnce();
    expect(api.planAiTask).not.toHaveBeenCalled();
    expect(api.executeAiTask).not.toHaveBeenCalled();
  });

  it('never silently falls back to normal chat when automatic classification fails', async () => {
    useAiChatStore.setState({ composerMode: 'auto' });
    api.classifyAiIntent.mockRejectedValue(new Error('intent unavailable'));
    await useAiChatStore.getState().send('帮我请假');
    expect(api.planAiTask).not.toHaveBeenCalled();
    expect(api.streamChat).not.toHaveBeenCalled();
    expect(api.sendChat).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().messagesByConversation[3][1]).toMatchObject({ status: 'failed' });
    expect(useAiChatStore.getState().messagesByConversation[3][1].content).toContain('自动判定未完成');
    expect(useAiChatStore.getState().generatingIds).toEqual([]);
  });

  it('does not start planning after the user stops an in-flight classification', async () => {
    useAiChatStore.setState({ activeId: 3, composerMode: 'auto' });
    let resolve!: (value: string) => void;
    api.classifyAiIntent.mockReturnValue(new Promise<string>((done) => { resolve = done; }));
    const pending = useAiChatStore.getState().send('查询资产');
    expect(api.classifyAiIntent).toHaveBeenCalledOnce();
    useAiChatStore.getState().stop(3);
    resolve('OPERATION');
    await pending;
    expect(api.planAiTask).not.toHaveBeenCalled();
    expect(api.executeAiTask).not.toHaveBeenCalled();
    expect(api.streamChat).not.toHaveBeenCalled();
    expect(useAiChatStore.getState().generatingIds).toEqual([]);
  });

  it('keeps explicit chat independent from automatic business detection even for todo wording', async () => {
    useAiChatStore.setState({ composerMode: 'chat' });
    api.streamChat.mockResolvedValue(undefined);
    await useAiChatStore.getState().send('查一下我的待办');
    expect(api.classifyAiIntent).not.toHaveBeenCalled();
    expect(api.planAiTask).not.toHaveBeenCalled();
    expect(api.streamChat).toHaveBeenCalledOnce();
  });

  it('defaults new sessions to automatic routing', () => {
    expect(useAiChatStore.getInitialState().composerMode).toBe('auto');
  });

  it('keeps attachment requests in chat without sending files to the intent or task API', async () => {
    useAiChatStore.setState({ activeId: 3, composerMode: 'auto', pendingAttachments: { 3: [{
      id: 7, type: 'file', name: 'notes.txt', size: 20, mimeType: 'text/plain',
      contentUrl: '/api/attachments/7', parsed: true, hasText: true, createdAt: '',
    }] } });
    api.streamChat.mockResolvedValue(undefined);
    await useAiChatStore.getState().send('总结附件');
    expect(api.classifyAiIntent).not.toHaveBeenCalled();
    expect(api.planAiTask).not.toHaveBeenCalled();
    expect(api.streamChat).toHaveBeenCalledOnce();
  });

  it('ignores duplicate sends while classification is in flight', async () => {
    useAiChatStore.setState({ activeId: 3, composerMode: 'auto' });
    let resolve!: (value: string) => void;
    api.classifyAiIntent.mockReturnValue(new Promise<string>((done) => { resolve = done; }));
    api.streamChat.mockResolvedValue(undefined);
    const pending = useAiChatStore.getState().send('解释预算');
    await useAiChatStore.getState().send('重复请求');
    resolve('CHAT');
    await pending;
    expect(api.classifyAiIntent).toHaveBeenCalledOnce();
    expect(api.streamChat).toHaveBeenCalledOnce();
  });
});
