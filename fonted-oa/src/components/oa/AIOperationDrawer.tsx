'use client';

import { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Drawer,
  Input,
  List,
  Modal,
  Result,
  Space,
  Steps,
  Tag,
  Typography,
  message,
} from 'antd';
import { RobotOutlined, SendOutlined, StopOutlined, ThunderboltOutlined } from '@ant-design/icons';
import type { AiTaskExecuteResponse, AiTaskPlanResponse, OaRole } from '@/types/oa';
import { executeAiTask, formatOaApiError, OaApiError, planAiTask } from '@/lib/oaApi';
import { getAllowedAiActions, isSensitiveEmployeeTask, roleDataScope } from '@/mock/oaPermissions';

interface AIOperationDrawerProps {
  open: boolean;
  role: OaRole;
  pageId: string;
  pageTitle: string;
  initialPrompt?: string;
  onClose: () => void;
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
  onExecuted,
}: AIOperationDrawerProps) {
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
      message.warning('请输入 AI 任务');
      return;
    }
    if (isSensitiveEmployeeTask(role, value)) {
      message.warning('当前角色无权限执行该操作');
      setMessages((prev) => [...prev, { role: 'user', content: value }, { role: 'assistant', content: '当前角色无权限执行该操作。' }]);
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
      message.success('AI 执行计划已生成');
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
      message.warning('请先生成执行计划');
      return;
    }
    if (role === 'employee' && ['approve', 'delete', 'export'].includes(plan.type)) {
      message.warning('当前角色无权限执行该操作');
      return;
    }

    Modal.confirm({
      title: '确认执行 AI 计划',
      content: `任务 ${plan.taskId} 风险等级为 ${plan.riskLevel}。确认后仅调用后端已注册的真实业务能力，并记录审计。`,
      okText: '确认执行',
      cancelText: '取消',
      onOk: async () => {
        try {
          setOperationError(null);
          const data = await executeAiTask({ taskId: plan.taskId, confirm: true });
          setResult(data);
          onExecuted(`AI 执行 ${plan.type}：${data.auditId}`);
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
    <Drawer title="AI 操作面板" width={520} open={open} onClose={onClose} extra={<Button onClick={onClose}>关闭 Drawer</Button>}>
      <Space direction="vertical" size={16} className="oa-drawer-stack">
        <Card size="small" title="当前上下文">
          <Descriptions
            size="small"
            column={1}
            items={[
              { key: 'page', label: '当前页面', children: pageTitle },
              { key: 'role', label: '当前角色', children: role },
              { key: 'scope', label: '数据范围', children: roleDataScope[role] },
              { key: 'confirm', label: '高风险动作', children: '需要二次确认' },
            ]}
          />
          <Space wrap className="oa-ai-tags">
            {allowedActions.length ? allowedActions.map((action) => <Tag color="blue" key={action.actionId}>{action.name}</Tag>) : <Tag>暂无可执行动作</Tag>}
          </Space>
        </Card>

        <Card size="small" title="快捷指令">
          <Space wrap>
            {['预审当前列表', '新建采购申请', '修改员工部门', '排查接口异常', '导出审批摘要'].map((command) => (
              <Button key={command} icon={<ThunderboltOutlined />} onClick={() => submitPlan(command)}>
                {command}
              </Button>
            ))}
          </Space>
        </Card>

        {role === 'employee' && (
          <Alert type="warning" showIcon message="普通员工角色下，审批、删除、权限修改、敏感导出等高风险 AI 操作会被拦截。" />
        )}

        {operationError && (
          <Alert
            type="error"
            showIcon
            message="AI 能力调用失败"
            description={operationError.message}
            action={operationError.retryable ? <Button size="small" onClick={() => submitPlan()}>重试</Button> : undefined}
          />
        )}

        <Card size="small" title="消息区">
          <List
            size="small"
            dataSource={messages}
            locale={{ emptyText: '输入任务后这里会显示用户消息与 AI 返回' }}
            renderItem={(item) => (
              <List.Item>
                <List.Item.Meta
                  avatar={<Tag color={item.role === 'user' ? 'geekblue' : 'purple'}>{item.role === 'user' ? '你' : 'AI'}</Tag>}
                  title={item.role === 'user' ? '用户输入' : 'AI 返回'}
                  description={item.content}
                />
              </List.Item>
            )}
          />
        </Card>

        <Input.TextArea
          rows={4}
          value={input}
          onChange={(event) => setInput(event.target.value)}
          placeholder="例如：帮我预审当前列表，并输出风险排序"
        />
        <Space wrap>
          <Button type="primary" icon={<SendOutlined />} loading={loading} onClick={() => submitPlan()}>
            发送 / 生成计划
          </Button>
          <Button icon={<StopOutlined />} onClick={() => {
            setPlan(null);
            setResult(null);
            message.info('已取消当前计划');
          }}>
            取消计划
          </Button>
        </Space>

        {plan && (
          <Card size="small" title="执行计划">
            <Typography.Paragraph>{plan.summary}</Typography.Paragraph>
            <Space wrap>
              <Tag color={plan.riskLevel === 'high' ? 'red' : 'orange'}>{plan.riskLevel}</Tag>
              <Tag color="processing">{plan.type}</Tag>
              {plan.requireConfirm && <Tag color="warning">需要确认</Tag>}
            </Space>
            <Steps
              direction="vertical"
              size="small"
              current={plan.steps.length - 1}
              items={plan.steps.map((step) => ({ title: step.title, description: step.description }))}
            />
            <Button type="primary" icon={<RobotOutlined />} onClick={confirmExecute}>
              确认执行
            </Button>
          </Card>
        )}

        {result && (
          <Result
            status="success"
            title={result.message}
            subTitle={`审计编号：${result.auditId}`}
            extra={[
              <Tag color="success" key="success">成功 {result.result.successCount}</Tag>,
              <Tag color="warning" key="pending">待确认 {result.result.pendingConfirmCount}</Tag>,
              <Tag color="error" key="reject">建议退回 {result.result.rejectSuggestCount}</Tag>,
            ]}
          />
        )}
      </Space>
    </Drawer>
  );
}
