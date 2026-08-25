'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert, App as AntdApp, Button, Card, Descriptions, Drawer, Empty, Input, Space, Steps, Tag, Timeline, Typography } from 'antd';
import type { AgentTaskStatus, AiTaskEvent, AiTaskExecuteResponse, AiTaskPlanResponse, OaRole } from '@/types/oa';
import { executeAiTask, formatOaApiError, issueAiTaskConfirmation, OaApiError, planAiTask, subscribeAiTaskEvents } from '@/lib/oaApi';
import { OaIcon } from '@/components/OaIcon';

interface AIOperationDrawerProps {
  open: boolean;
  role: OaRole;
  pageId: string;
  pageTitle: string;
  initialPrompt?: string;
  onClose: () => void;
  onOpenChangeComplete?: (open: boolean) => void;
  onExecuted: (text: string) => void;
}

interface ChatLine { role: 'user' | 'assistant'; content: string }

const TERMINAL_STATUSES = new Set<AgentTaskStatus>([
  'SUCCEEDED', 'PARTIALLY_SUCCEEDED', 'FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED',
]);
const AGENT_STATUSES = new Set<AgentTaskStatus>([
  'RECEIVED', 'PLANNING', 'PLAN_READY', 'WAITING_CONFIRMATION', 'QUEUED', 'RUNNING',
  ...TERMINAL_STATUSES,
]);

const PAGE_CAPABILITIES: Record<string, string[]> = {
  dashboard: ['todo', 'notification'],
  'todo-list': ['todo'],
  'my-applications': ['leave'],
  'knowledge-base': ['knowledge'],
  'message-center': ['notification'],
};

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

export default function AIOperationDrawer({ open, role, pageId, pageTitle, initialPrompt, onClose, onOpenChangeComplete, onExecuted }: AIOperationDrawerProps) {
  const { t } = useTranslation();
  const { message, modal } = AntdApp.useApp();
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatLine[]>([]);
  const [plan, setPlan] = useState<AiTaskPlanResponse | null>(null);
  const [execution, setExecution] = useState<AiTaskExecuteResponse | null>(null);
  const [taskStatus, setTaskStatus] = useState<AgentTaskStatus | null>(null);
  const [events, setEvents] = useState<AiTaskEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [operationError, setOperationError] = useState<{ message: string; retryable: boolean } | null>(null);
  const confirmationTokenRef = useRef<string | null>(null);
  const unsubscribeRef = useRef<(() => void) | null>(null);
  const capabilities = useMemo(() => PAGE_CAPABILITIES[pageId] ?? [], [pageId]);

  const stopEventStream = () => {
    unsubscribeRef.current?.();
    unsubscribeRef.current = null;
  };

  useEffect(() => () => stopEventStream(), []);
  useEffect(() => {
    if (open && initialPrompt) setInput(initialPrompt);
    if (!open) {
      confirmationTokenRef.current = null;
      stopEventStream();
    }
  }, [open, initialPrompt]);

  const resetExecution = () => {
    confirmationTokenRef.current = null;
    stopEventStream();
    setExecution(null);
    setTaskStatus(null);
    setEvents([]);
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
      const nextPlan = await planAiTask({ input: value, pageId });
      setPlan(nextPlan);
      setTaskStatus(nextPlan.status);
      setMessages((previous) => [...previous, { role: 'assistant', content: nextPlan.summary }]);
      message.success(t('oa.ai.planGenerated'));
    } catch (error) {
      const errorMessage = formatOaApiError(error);
      setPlan(null);
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
          if (status === 'SUCCEEDED' || status === 'PARTIALLY_SUCCEEDED') {
            onExecuted(t('oa.ai.executionCompleted', { taskId, status: t(`oa.ai.status.${status}`) }));
            message.success(t('oa.ai.executionCompletedMessage'));
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
    if (!plan.confirmationRequired) {
      void runPlan(false).catch(() => undefined);
      return;
    }
    modal.confirm({
      title: t('oa.ai.confirmTitle'),
      content: t('oa.ai.confirmContent', { taskId: plan.taskId, riskLevel: plan.riskLevel }),
      okText: t('oa.ai.confirmOk'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: plan.riskLevel === 'L2' },
      onOk: () => runPlan(true),
    });
  };

  const closeDrawer = () => {
    confirmationTokenRef.current = null;
    stopEventStream();
    onClose();
  };

  return (
    <Drawer rootClassName="oa-ai-operation-drawer" title={t('oa.ai.panelTitle')} size="default" styles={{ wrapper: { width: 540 } }} open={open} onClose={closeDrawer} afterOpenChange={onOpenChangeComplete}
      footer={<Space orientation="vertical" size={10} className="oa-ai-composer">
        <Input.TextArea rows={4} maxLength={4096} showCount value={input} onChange={(event) => setInput(event.target.value)} placeholder={t('oa.ai.placeholder')} />
        <Space wrap>
          <Button type="primary" icon={<OaIcon name="send" />} loading={loading} disabled={executing} onClick={() => submitPlan()}>{t('oa.ai.send')}</Button>
          <Button icon={<OaIcon name="pause" />} disabled={loading} onClick={() => { resetExecution(); setPlan(null); message.info(t('oa.ai.cancelledPlan')); }}>{t('oa.ai.cancelPlan')}</Button>
        </Space>
      </Space>}
    >
      <Space orientation="vertical" size={16} className="oa-drawer-stack">
        <Card size="small" className="oa-ai-context-card" title={t('oa.ai.contextTitle')}>
          <Descriptions size="small" column={1} items={[
            { key: 'page', label: t('oa.ai.currentPage'), children: pageTitle },
            { key: 'role', label: t('oa.ai.currentRole'), children: role },
            { key: 'scope', label: t('oa.ai.dataScope'), children: t('oa.ai.serverVerifiedScope') },
            { key: 'boundary', label: t('oa.ai.securityBoundary'), children: t('oa.ai.gatewayEnforced') },
          ]} />
          <Space wrap className="oa-ai-tags">
            {capabilities.length ? capabilities.map((capability) => <Tag color="blue" key={capability}>{t(`oa.ai.capabilities.${capability}`)}</Tag>) : <Tag>{t('oa.ai.noActions')}</Tag>}
          </Space>
        </Card>

        {capabilities.length > 0 && <Card size="small" title={t('oa.ai.quickCommands')}>
          <Space wrap>{capabilities.map((capability) => {
            const command = t(`oa.ai.commands.${capability}`);
            return <Button key={capability} icon={<OaIcon name="ai" />} disabled={loading || executing} onClick={() => submitPlan(command)}>{command}</Button>;
          })}</Space>
        </Card>}

        {operationError && <Alert type="error" showIcon title={t('oa.ai.callFailed')} description={operationError.message} action={operationError.retryable ? <Button size="small" onClick={() => submitPlan()}>{t('common.retry')}</Button> : undefined} />}

        <Card size="small" title={t('oa.ai.messageArea')}>
          {messages.length === 0 ? <Empty description={t('oa.ai.emptyMessages')} /> : <ul className="oa-ai-message-list">
            {messages.map((item, index) => <li key={`${item.role}-${index}`} className="oa-ai-message-item">
              <div className="oa-ai-message-meta"><Tag color={item.role === 'user' ? 'geekblue' : 'purple'}>{item.role === 'user' ? t('oa.ai.you') : t('oa.ai.assistant')}</Tag><Typography.Text type="secondary">{item.role === 'user' ? t('oa.ai.userInput') : t('oa.ai.aiReply')}</Typography.Text></div>
              <Typography.Paragraph className="oa-ai-message-content">{item.content}</Typography.Paragraph>
            </li>)}
          </ul>}
        </Card>

        {plan && <Card size="small" className="oa-ai-plan-card" title={t('oa.ai.planTitle')}>
          <div className="oa-ai-plan-heading"><Typography.Paragraph>{plan.summary}</Typography.Paragraph><Space wrap>
            <Tag color={plan.riskLevel === 'L2' ? 'red' : plan.riskLevel === 'L1' ? 'gold' : 'green'}>{plan.riskLevel}</Tag>
            <Tag color={statusColor(taskStatus ?? plan.status)}>{t(`oa.ai.status.${taskStatus ?? plan.status}`)}</Tag>
            {plan.confirmationRequired && <Tag color="warning">{t('oa.ai.requireConfirmTag')}</Tag>}
          </Space></div>
          <Steps orientation="vertical" size="small" current={taskStatus === 'RUNNING' ? Math.max(0, events.filter((event) => event.type === 'step-completed').length) : -1}
            items={plan.steps.map((step) => ({ title: step.title, content: t('oa.ai.planStepDescription', { sequence: step.sequence }) }))} />
          <Button type="primary" icon={<OaIcon name="ai" />} loading={executing} disabled={Boolean(execution)} onClick={requestExecution}>{plan.confirmationRequired ? t('oa.ai.confirmExecute') : t('oa.ai.executePlan')}</Button>
        </Card>}

        {execution && <Card size="small" className="oa-ai-progress-card" title={t('oa.ai.progressTitle')}>
          <div className="oa-ai-status-strip"><span><Typography.Text type="secondary">{t('oa.ai.taskId')}</Typography.Text><Typography.Text copyable={{ text: execution.taskId }}>{execution.taskId}</Typography.Text></span>{taskStatus && <Tag color={statusColor(taskStatus)}>{t(`oa.ai.status.${taskStatus}`)}</Tag>}</div>
          {events.length === 0 ? <Alert type="info" showIcon title={t('oa.ai.waitingForEvents')} /> : <Timeline className="oa-ai-event-feed" items={events.map((event) => ({ color: event.type === 'task-failed' ? 'red' : event.type === 'task-completed' ? 'green' : 'blue', children: t(`oa.ai.events.${event.type}`, { defaultValue: t('oa.ai.events.update') }) }))} />}
        </Card>}
      </Space>
    </Drawer>
  );
}
