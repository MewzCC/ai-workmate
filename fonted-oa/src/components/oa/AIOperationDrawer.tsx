'use client';

import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
  Modal,
  Result,
  Space,
  Steps,
  Tag,
  Typography,
  message,
} from 'antd';
import type { AiTaskExecuteResponse, AiTaskPlanResponse, OaRole } from '@/types/oa';
import { executeAiTask, formatOaApiError, OaApiError, planAiTask } from '@/lib/oaApi';
import { getAllowedAiActions, isSensitiveEmployeeTask, roleDataScope } from '@/mock/oaPermissions';
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

interface ChatLine {
  role: 'user' | 'assistant';
  content: string;
}

export default function AIOperationDrawer({
  open,
  role,
  pageId,
  pageTitle,
  initialPrompt,
  onClose,
  onOpenChangeComplete,
  onExecuted,
}: AIOperationDrawerProps) {
  const { t } = useTranslation();
  const [input, setInput] = useState('');
  const [messages, setMessages] = useState<ChatLine[]>([]);
  const [plan, setPlan] = useState<AiTaskPlanResponse | null>(null);
  const [result, setResult] = useState<AiTaskExecuteResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [operationError, setOperationError] = useState<{ message: string; retryable: boolean } | null>(null);
  const allowedActions = getAllowedAiActions(role, pageId);

  useEffect(() => {
    if (open && initialPrompt) {
      setInput(initialPrompt);
    }
  }, [open, initialPrompt]);

  const submitPlan = async (preset?: string) => {
    const value = (preset || input).trim();
    if (!value) {
      message.warning(t('oa.ai.enterTask'));
      return;
    }
    if (isSensitiveEmployeeTask(role, value)) {
      message.warning(t('oa.ai.noPermission'));
      setMessages((prev) => [...prev, { role: 'user', content: value }, { role: 'assistant', content: t('oa.ai.noPermission') }]);
      return;
    }

    setLoading(true);
    setOperationError(null);
    setResult(null);
    setMessages((prev) => [...prev, { role: 'user', content: value }]);

    try {
      const nextPlan = await planAiTask({ input: value, pageId });
      setPlan(nextPlan);
      setMessages((prev) => [...prev, { role: 'assistant', content: nextPlan.summary }]);
      message.success(t('oa.ai.planGenerated'));
    } catch (error) {
      const errorMessage = formatOaApiError(error);
      setPlan(null);
      setOperationError({ message: errorMessage, retryable: error instanceof OaApiError && error.retryable });
      setMessages((prev) => [...prev, { role: 'assistant', content: errorMessage }]);
      message.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const confirmExecute = () => {
    if (!plan) {
      message.warning(t('oa.ai.generatePlanFirst'));
      return;
    }
    if (role === 'employee' && ['approve', 'delete', 'export'].includes(plan.type)) {
      message.warning(t('oa.ai.noPermission'));
      return;
    }

    Modal.confirm({
      title: t('oa.ai.confirmTitle'),
      content: t('oa.ai.confirmContent', { taskId: plan.taskId, riskLevel: plan.riskLevel }),
      okText: t('oa.ai.confirmOk'),
      cancelText: t('common.cancel'),
      onOk: async () => {
        try {
          setOperationError(null);
          const data = await executeAiTask({ taskId: plan.taskId, confirm: true });
          setResult(data);
          onExecuted(t('oa.ai.executedAudit', { type: plan.type, auditId: data.auditId }));
          message.success(data.message);
        } catch (error) {
          const errorMessage = formatOaApiError(error);
          setResult(null);
          setOperationError({ message: errorMessage, retryable: error instanceof OaApiError && error.retryable });
          message.error(errorMessage);
        }
      },
    });
  };

  return (
    <Drawer
      rootClassName="oa-ai-operation-drawer"
      title={t('oa.ai.panelTitle')}
      size="default"
      styles={{ wrapper: { width: 520 } }}
      open={open}
      onClose={onClose}
      afterOpenChange={onOpenChangeComplete}
      footer={(
        <Space orientation="vertical" size={10} className="oa-ai-composer">
          <Input.TextArea
            rows={4}
            value={input}
            onChange={(event) => setInput(event.target.value)}
            placeholder={t('oa.ai.placeholder')}
          />
          <Space wrap>
            <Button type="primary" icon={<OaIcon name="send" />} loading={loading} onClick={() => submitPlan()}>
              {t('oa.ai.send')}
            </Button>
            <Button icon={<OaIcon name="pause" />} onClick={() => {
              setPlan(null);
              setResult(null);
              message.info(t('oa.ai.cancelledPlan'));
            }}>
              {t('oa.ai.cancelPlan')}
            </Button>
          </Space>
        </Space>
      )}
    >
      <Space orientation="vertical" size={16} className="oa-drawer-stack">
        <Card size="small" title={t('oa.ai.contextTitle')}>
          <Descriptions
            size="small"
            column={1}
            items={[
              { key: 'page', label: t('oa.ai.currentPage'), children: pageTitle },
              { key: 'role', label: t('oa.ai.currentRole'), children: role },
              { key: 'scope', label: t('oa.ai.dataScope'), children: roleDataScope[role] },
              { key: 'confirm', label: t('oa.ai.highRiskActions'), children: t('oa.ai.requiresConfirm') },
            ]}
          />
          <Space wrap className="oa-ai-tags">
            {allowedActions.length ? allowedActions.map((action) => <Tag color="blue" key={action.actionId}>{action.name}</Tag>) : <Tag>{t('oa.ai.noActions')}</Tag>}
          </Space>
        </Card>

        <Card size="small" title={t('oa.ai.quickCommands')}>
          <Space wrap>
            {(t('oa.ai.commands', { returnObjects: true }) as string[]).map((command) => (
              <Button key={command} icon={<OaIcon name="ai" />} onClick={() => submitPlan(command)}>
                {command}
              </Button>
            ))}
          </Space>
        </Card>

        {role === 'employee' && (
          <Alert type="warning" showIcon title={t('oa.ai.employeeWarning')} />
        )}

        {operationError && (
          <Alert
            type="error"
            showIcon
            title={t('oa.ai.callFailed')}
            description={operationError.message}
            action={operationError.retryable ? <Button size="small" onClick={() => submitPlan()}>{t('common.retry')}</Button> : undefined}
          />
        )}

        <Card size="small" title={t('oa.ai.messageArea')}>
          {messages.length === 0 ? (
            <Empty description={t('oa.ai.emptyMessages')} />
          ) : (
            <ul className="oa-ai-message-list">
              {messages.map((item, index) => (
                <li key={index} className="oa-ai-message-item">
                  <div className="oa-ai-message-meta">
                    <Tag color={item.role === 'user' ? 'geekblue' : 'purple'}>{item.role === 'user' ? t('oa.ai.you') : 'AI'}</Tag>
                    <Typography.Text type="secondary">{item.role === 'user' ? t('oa.ai.userInput') : t('oa.ai.aiReply')}</Typography.Text>
                  </div>
                  <Typography.Paragraph className="oa-ai-message-content">{item.content}</Typography.Paragraph>
                </li>
              ))}
            </ul>
          )}
        </Card>

        {plan && (
          <Card size="small" title={t('oa.ai.planTitle')}>
            <Typography.Paragraph>{plan.summary}</Typography.Paragraph>
            <Space wrap>
              <Tag color={plan.riskLevel === 'high' ? 'red' : 'orange'}>{plan.riskLevel}</Tag>
              <Tag color="processing">{plan.type}</Tag>
              {plan.requireConfirm && <Tag color="warning">{t('oa.ai.requireConfirmTag')}</Tag>}
            </Space>
            <Steps
              direction="vertical"
              size="small"
              current={plan.steps.length - 1}
              items={plan.steps.map((step) => ({ title: step.title, description: step.description }))}
            />
            <Button type="primary" icon={<OaIcon name="ai" />} onClick={confirmExecute}>
              {t('oa.ai.confirmExecute')}
            </Button>
          </Card>
        )}

        {result && (
          <Result
            status="success"
            title={result.message}
            subTitle={t('oa.ai.auditNo', { id: result.auditId })}
            extra={[
              <Tag color="success" key="success">{t('oa.ai.successTag', { count: result.result.successCount })}</Tag>,
              <Tag color="warning" key="pending">{t('oa.ai.pendingTag', { count: result.result.pendingConfirmCount })}</Tag>,
              <Tag color="error" key="reject">{t('oa.ai.rejectTag', { count: result.result.rejectSuggestCount })}</Tag>,
            ]}
          />
        )}
      </Space>
    </Drawer>
  );
}
