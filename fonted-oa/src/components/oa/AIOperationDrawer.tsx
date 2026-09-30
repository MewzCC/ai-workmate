'use client';

import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, App as AntdApp, Button, Card, Drawer, Empty, Input, Space, Spin, Tag, Timeline, Typography } from 'antd';
import type { AgentTaskDetail, AgentTaskStatus, AiTaskEvent, AiTaskExecuteResponse, AiTaskPlanResponse, OaRole, PageCapability } from '@/types/oa';
import { sanitizePageAgentContext, type PageAgentContextSnapshot } from './PageAgentContext';
import PageAgentCapabilityPanel from './PageAgentCapabilityPanel';
import { agentTaskApi, executeAiTask, formatOaApiError, getPageCapabilities, issueAiTaskConfirmation, OaApiError, planAiTask, subscribeAiTaskEvents } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';
import AgentPlanPreview from './AgentPlanPreview';

interface AIOperationDrawerProps {
  open: boolean;
  role: OaRole;
  pageId: string;
  pageTitle: string;
  pageContext?: PageAgentContextSnapshot;
  initialPrompt?: string;
  onClose: () => void;
  onOpenChangeComplete?: (open: boolean) => void;
  onExecutionCompleted?: () => void;
  onNavigatePage?: (pageId: string) => boolean;
}

interface ChatLine { role: 'user' | 'assistant'; content: string }

const TERMINAL_STATUSES = new Set<AgentTaskStatus>([
  'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED',
]);
const AGENT_STATUSES = new Set<AgentTaskStatus>([
  'RECEIVED', 'PLANNING', 'PLAN_READY', 'WAITING_CONFIRMATION', 'QUEUED', 'RUNNING',
  ...TERMINAL_STATUSES,
]);

function statusColor(status: AgentTaskStatus): string {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'PARTIALLY_SUCCEEDED') return 'warning';
  if (TERMINAL_STATUSES.has(status)) return 'error';
  if (status === 'RUNNING') return 'processing';
  return 'default';
}

function eventStatus(event: AiTaskEvent): AgentTaskStatus | null {
  const status = event.data.status;
  return typeof status === 'string' && AGENT_STATUSES.has(status as AgentTaskStatus)
    ? status as AgentTaskStatus
    : null;
}

function resultPage(result: unknown): { total: number; empty: boolean } | null {
  if (!result || typeof result !== 'object' || Array.isArray(result)) return null;
  const page = result as Record<string, unknown>;
  return typeof page.total === 'number' && Array.isArray(page.items)
    ? { total: page.total, empty: page.items.length === 0 }
    : null;
}

export default function AIOperationDrawer({ open, role, pageId, pageTitle, pageContext, initialPrompt, onClose, onOpenChangeComplete, onExecutionCompleted, onNavigatePage }: AIOperationDrawerProps) {
  const { t } = useTranslation();
  const { message, modal } = AntdApp.useApp();
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatLine[]>([]);
  const [plan, setPlan] = useState<AiTaskPlanResponse | null>(null);
  const [planPage, setPlanPage] = useState<{ id: string; title: string } | null>(null);
  const [execution, setExecution] = useState<AiTaskExecuteResponse | null>(null);
  const [taskStatus, setTaskStatus] = useState<AgentTaskStatus | null>(null);
  const [events, setEvents] = useState<AiTaskEvent[]>([]);
  const [resultDetail, setResultDetail] = useState<AgentTaskDetail | null>(null);
  const [resultLoading, setResultLoading] = useState(false);
  const [resultError, setResultError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [operationError, setOperationError] = useState<{ message: string; retryable: boolean } | null>(null);
  const [capability, setCapability] = useState<PageCapability | null>(null);
  const [capabilityLoading, setCapabilityLoading] = useState(false);
  const [capabilityError, setCapabilityError] = useState<string | null>(null);
  const [capabilityReload, setCapabilityReload] = useState(0);
  const confirmationTokenRef = useRef<string | null>(null);
  const unsubscribeRef = useRef<(() => void) | null>(null);
  const refreshedTaskRef = useRef<string | null>(null);
  const loadedResultTaskRef = useRef<string | null>(null);
  const resultRequestRef = useRef(0);
  const resultCardRef = useRef<HTMLDivElement | null>(null);
  const answeredTaskRef = useRef<string | null>(null);
  const currentPageIdRef = useRef(pageId);
  currentPageIdRef.current = pageId;

  const stopEventStream = () => {
    unsubscribeRef.current?.();
    unsubscribeRef.current = null;
  };

  useEffect(() => () => stopEventStream(), []);
  useEffect(() => () => { resultRequestRef.current += 1; }, []);
  useEffect(() => {
    if (resultDetail || resultError) resultCardRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' });
  }, [resultDetail, resultError]);
  useEffect(() => {
    if (open && initialPrompt) setInput(initialPrompt);
    if (!open) {
      confirmationTokenRef.current = null;
    }
  }, [open, initialPrompt]);

  useEffect(() => {
    if (!open) return undefined;
    let active = true;
    setCapability(null);
    setCapabilityError(null);
    setCapabilityLoading(true);
    getPageCapabilities(pageId)
      .then((nextCapability) => {
        if (active) setCapability(nextCapability);
      })
      .catch((error) => {
        if (active) setCapabilityError(formatOaApiError(error));
      })
      .finally(() => {
        if (active) setCapabilityLoading(false);
      });
    return () => { active = false; };
  }, [open, pageId, capabilityReload]);

  const resetExecution = () => {
    resultRequestRef.current += 1;
    loadedResultTaskRef.current = null;
    confirmationTokenRef.current = null;
    stopEventStream();
    setExecution(null);
    setTaskStatus(null);
    setEvents([]);
    setResultDetail(null);
    setResultLoading(false);
    setResultError(null);
  };

  const loadResult = async (taskId: string) => {
    const request = ++resultRequestRef.current;
    setResultLoading(true);
    setResultError(null);
    try {
      const detail = await agentTaskApi.detail(taskId);
      if (request !== resultRequestRef.current) return;
      if (detail.taskId !== taskId || !TERMINAL_STATUSES.has(detail.status)) {
        throw new Error(t('oa.ai.resultNotReady'));
      }
      setResultDetail(detail);
      setTaskStatus(detail.status);
      const todoStep = detail.status === 'SUCCEEDED'
        ? detail.steps.find((step) => step.toolCode === 'todo.query' && step.status === 'SUCCEEDED')
        : null;
      if (todoStep?.resultSummary && answeredTaskRef.current !== taskId) {
        answeredTaskRef.current = taskId;
        setMessages((previous) => [...previous, { role: 'assistant', content: todoStep.resultSummary! }]);
        if ((todoStep.arguments?.status == null || todoStep.arguments.status === 'PENDING')
            && todoStep.arguments?.from == null && todoStep.arguments?.to == null) {
          onNavigatePage?.('todo');
        }
      }
    } catch (error) {
      if (request !== resultRequestRef.current) return;
      loadedResultTaskRef.current = null;
      setResultError(error instanceof OaApiError ? formatOaApiError(error) : t('oa.ai.resultNotReady'));
    } finally {
      if (request === resultRequestRef.current) setResultLoading(false);
    }
  };

  const submitPlan = async (preset?: string) => {
    const value = (preset || input).trim();
    if (!value) {
      message.warning(t('oa.ai.enterTask'));
      return;
    }
    resetExecution();
    setLoading(true);
    setOperationError(null);
    setMessages((previous) => [...previous, { role: 'user', content: value }]);
    try {
      const safePageContext = pageContext && capability
        ? sanitizePageAgentContext(pageContext, capability.contextSchema)
        : undefined;
      const nextPlan = await planAiTask({
        input: value,
        pageId,
        ...(safePageContext && Object.keys(safePageContext).length > 0
          ? { pageContext: safePageContext }
          : {}),
      });
      setPlan(nextPlan);
      setPlanPage({ id: pageId, title: pageTitle });
      setTaskStatus(nextPlan.status);
      setInput((current) => current.trim() === value ? '' : current);
      setMessages((previous) => [...previous, { role: 'assistant', content: nextPlan.summary }]);
      message.success(t('oa.ai.planGenerated'));
    } catch (error) {
      const errorMessage = formatOaApiError(error);
      setPlan(null);
      setPlanPage(null);
      setOperationError({ message: errorMessage, retryable: error instanceof OaApiError && error.retryable });
      setMessages((previous) => [...previous, { role: 'assistant', content: errorMessage }]);
      message.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const beginEventStream = (taskId: string) => {
    stopEventStream();
    unsubscribeRef.current = subscribeAiTaskEvents(taskId, {
      onEvent: (event) => {
        setEvents((previous) => [...previous, event]);
        const status = eventStatus(event);
        if (!status) return;
        setTaskStatus(status);
        if (TERMINAL_STATUSES.has(status)) {
          setExecuting(false);
          confirmationTokenRef.current = null;
          if (loadedResultTaskRef.current !== taskId) {
            loadedResultTaskRef.current = taskId;
            void loadResult(taskId);
            if (status === 'SUCCEEDED' || status === 'PARTIALLY_SUCCEEDED') {
              message.success(t('oa.ai.executionCompletedMessage'));
            }
          }
          if (status === 'SUCCEEDED' || status === 'PARTIALLY_SUCCEEDED') {
            if (refreshedTaskRef.current !== taskId) {
              refreshedTaskRef.current = taskId;
              onExecutionCompleted?.();
            }
          }
        }
      },
      onError: (error) => {
        const errorMessage = formatOaApiError(error);
        setExecuting(false);
        setOperationError({ message: errorMessage, retryable: error.retryable });
      },
    });
  };

  const runPlan = async (withConfirmation: boolean) => {
    if (!plan) return;
    if (planPage?.id !== currentPageIdRef.current) {
      message.warning(t('oa.ai.planFromOtherPage'));
      return;
    }
    setExecuting(true);
    setOperationError(null);
    try {
      if (withConfirmation) {
        const credential = await issueAiTaskConfirmation(plan.taskId, { planVersion: plan.planVersion, planHash: plan.planHash });
        confirmationTokenRef.current = credential.token;
      }
      const data = await executeAiTask(plan.taskId, {
        planVersion: plan.planVersion,
        planHash: plan.planHash,
        ...(confirmationTokenRef.current ? { confirmationToken: confirmationTokenRef.current } : {}),
      });
      confirmationTokenRef.current = null;
      refreshedTaskRef.current = null;
      setExecution(data);
      setTaskStatus(data.status);
      beginEventStream(data.taskId);
      message.success(t('oa.ai.executionQueued'));
    } catch (error) {
      confirmationTokenRef.current = null;
      setExecuting(false);
      const errorMessage = formatOaApiError(error);
      setOperationError({ message: errorMessage, retryable: error instanceof OaApiError && error.retryable });
      message.error(errorMessage);
      throw error;
    }
  };

  const requestExecution = () => {
    if (!plan) {
      message.warning(t('oa.ai.generatePlanFirst'));
      return;
    }
    if (planPage?.id !== pageId) {
      message.warning(t('oa.ai.planFromOtherPage'));
      return;
    }
    if (!plan.confirmationRequired) {
      void runPlan(false).catch(() => undefined);
      return;
    }
    modal.confirm({
      title: t('oa.ai.confirmTitle'),
      content: <Space orientation="vertical" size={12}>
        <Typography.Text>{t('oa.ai.confirmContent', { taskId: plan.taskId, riskLevel: plan.riskLevel })}</Typography.Text>
        <AgentPlanPreview plan={plan} status={taskStatus ?? plan.status} />
      </Space>,
      okText: t('oa.ai.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: plan.riskLevel === 'L2' },
      onOk: () => runPlan(true),
    });
  };

  const closeDrawer = () => {
    confirmationTokenRef.current = null;
    onClose();
  };

  return (
    <Drawer rootClassName="oa-ai-operation-drawer" title={t('oa.ai.panelTitle')} size="default" styles={{ wrapper: { width: 540 } }} open={open} onClose={closeDrawer} afterOpenChange={onOpenChangeComplete}
      footer={<Space orientation="vertical" size={10} className="oa-ai-composer">
        <Input.TextArea rows={4} maxLength={4096} showCount value={input} onChange={(event) => setInput(event.target.value)} placeholder={t('oa.ai.placeholder')} />
        <Space wrap>
          <Button type="primary" icon={<OaIcon name="send" />} loading={loading} disabled={executing} onClick={() => submitPlan()}>{t('oa.ai.send')}</Button>
          <Button icon={<OaIcon name="pause" />} disabled={loading} onClick={() => { resetExecution(); setPlan(null); setPlanPage(null); message.info(t('oa.ai.cancelledPlan')); }}>{t('oa.ai.cancelPlan')}</Button>
        </Space>
      </Space>}
    >
      <Space orientation="vertical" size={16} className="oa-drawer-stack">
        <PageAgentCapabilityPanel
          pageTitle={pageTitle}
          role={role}
          capability={capability}
          loading={capabilityLoading}
          commandDisabled={loading || executing}
          onSelectTool={(prompt) => submitPlan(prompt)}
        />

        {capabilityError && <Alert type="error" showIcon title={t('oa.ai.capabilityLoadFailed')} description={capabilityError}
          action={<Button size="small" onClick={() => setCapabilityReload((value) => value + 1)}>{t('common.retry')}</Button>} />}

        {operationError && <Alert type="error" showIcon title={t('oa.ai.callFailed')} description={operationError.message} action={operationError.retryable ? <Button size="small" onClick={() => submitPlan()}>{t('common.retry')}</Button> : undefined} />}

        <Card size="small" title={t('oa.ai.messageArea')}>
          {messages.length === 0 ? <Empty description={t('oa.ai.emptyMessages')} /> : <ul className="oa-ai-message-list">
            {messages.map((item, index) => <li key={`${item.role}-${index}`} className="oa-ai-message-item">
              <div className="oa-ai-message-meta"><Tag color={item.role === 'user' ? 'geekblue' : 'purple'}>{item.role === 'user' ? t('oa.ai.you') : t('oa.ai.assistant')}</Tag><Typography.Text type="secondary">{item.role === 'user' ? t('oa.ai.userInput') : t('oa.ai.aiReply')}</Typography.Text></div>
              <Typography.Paragraph className="oa-ai-message-content">{item.content}</Typography.Paragraph>
            </li>)}
          </ul>}
        </Card>

        {execution && (resultLoading || resultError || resultDetail) && <div ref={resultCardRef}><Card size="small" className="oa-ai-result-card" title={t('oa.ai.resultTitle')}>
          {resultLoading && <Spin description={t('oa.ai.resultLoading')}><div className="oa-ai-result-loading" /></Spin>}
          {resultError && <Alert type="error" showIcon title={t('oa.ai.resultLoadFailed')} description={resultError}
            action={<Button size="small" onClick={() => { loadedResultTaskRef.current = execution.taskId; void loadResult(execution.taskId); }}>{t('common.retry')}</Button>} />}
          {resultDetail && <Space orientation="vertical" size={12} className="oa-ai-result-content">
            <Tag color={statusColor(resultDetail.status)}>{t(`oa.ai.status.${resultDetail.status}`)}</Tag>
            {resultDetail.errorCode && <Alert type="error" showIcon title={t('oa.ai.executionFailed')} description={resultDetail.errorCode} />}
            {resultDetail.steps.map((step) => <div className="oa-ai-result-step" key={step.sequence}>
              <div className="oa-ai-result-step-heading"><Typography.Text strong>{t('oa.ai.resultStep', { sequence: step.sequence })} · {plan?.steps.find((item) => item.sequence === step.sequence)?.title ?? step.toolCode}</Typography.Text><Tag color={step.status === 'SUCCEEDED' ? 'success' : 'error'}>{step.status === 'SUCCEEDED' ? t('oa.ai.status.SUCCEEDED') : t('oa.ai.status.FAILED')}</Tag></div>
              {step.resultSummary && <Typography.Paragraph>{step.resultSummary}</Typography.Paragraph>}
              {resultPage(step.result) && <Typography.Paragraph type="secondary">{t('oa.ai.resultCount', { count: resultPage(step.result)?.total })}</Typography.Paragraph>}
              {resultPage(step.result)?.empty && <Empty description={t('oa.ai.resultEmpty')} image={Empty.PRESENTED_IMAGE_SIMPLE} />}
              {step.result != null && !step.resultSummary && !resultPage(step.result)?.empty && <pre className="oa-ai-result-json">{JSON.stringify(step.result, null, 2)}</pre>}
              {step.result == null && <Typography.Text type="secondary">{step.errorCode || t('oa.ai.noStepResult')}</Typography.Text>}
            </div>)}
          </Space>}
        </Card></div>}

        {plan && <Card size="small" className="oa-ai-plan-card" title={t('oa.ai.planTitle')}>
          {planPage?.id !== pageId && <Alert type="warning" showIcon title={t('oa.ai.planFromOtherPage')} description={t('oa.ai.planSourcePage', { page: planPage?.title })} />}
          <AgentPlanPreview
            plan={plan}
            status={taskStatus ?? plan.status}
            completedSteps={events.filter((event) => event.type === 'step-completed').length}
          />
          <Button type="primary" icon={<OaIcon name="ai" />} loading={executing} disabled={Boolean(execution) || planPage?.id !== pageId} onClick={requestExecution}>{plan.confirmationRequired ? t('oa.ai.confirmExecute') : t('oa.ai.executePlan')}</Button>
        </Card>}

        {execution && <Card size="small" className="oa-ai-progress-card" title={t('oa.ai.progressTitle')}>
          <div className="oa-ai-status-strip"><span><Typography.Text type="secondary">{t('oa.ai.taskId')}</Typography.Text><Typography.Text copyable={{ text: execution.taskId }}>{execution.taskId}</Typography.Text></span>{taskStatus && <Tag color={statusColor(taskStatus)}>{t(`oa.ai.status.${taskStatus}`)}</Tag>}</div>
          {events.length === 0 ? <Alert type="info" showIcon title={t('oa.ai.waitingForEvents')} /> : <Timeline className="oa-ai-event-feed" items={events.map((event) => ({ color: event.type === 'task-failed' ? 'red' : event.type === 'task-completed' ? 'green' : 'blue', content: t(`oa.ai.events.${event.type}`, { defaultValue: t('oa.ai.events.update') }) }))} />}
        </Card>}
      </Space>
    </Drawer>
  );
}
