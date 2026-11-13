import { Alert, Button, Card, Space, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import type { AgentTaskDetail } from '@/types/oa';
import { isAgentTaskTerminal } from '@/lib/agentOperationState';
import { canCancelAgentTask } from '@/lib/agentTaskCancellation';

interface Props { task: AgentTaskDetail; observing: boolean; onRefresh: () => Promise<void>; onCancel: () => Promise<void> }

export default function AgentOperationStatusCard({ task, observing, onRefresh, onCancel }: Props) {
  const { t } = useTranslation();
  const unknown = task.status === 'PARTIALLY_SUCCEEDED';
  const finished = isAgentTaskTerminal(task.status);
  return <Card size="small" className="ai-operation-status" title={t('chat.operationStatusTitle')}>
    <Space orientation="vertical" style={{ width: '100%' }}>
      <Space wrap><Tag color={unknown ? 'warning' : task.status === 'SUCCEEDED' ? 'success' : finished ? 'default' : 'processing'}>
        {t(`oa.ai.status.${task.status}`)}</Tag><Typography.Text copyable>{task.taskId}</Typography.Text></Space>
      <Alert showIcon type={unknown ? 'warning' : 'info'}
        title={t(unknown ? 'chat.operationResultUnknown' : finished ? 'chat.operationRecordedState' : 'chat.operationStillRunning')} />
      <Space wrap>
        <Button loading={observing} onClick={() => void onRefresh()}>{t('chat.operationRefresh')}</Button>
        {canCancelAgentTask(task.status) ? <Button onClick={() => void onCancel()}>{t('chat.operationDiscard')}</Button> : null}
      </Space>
    </Space>
  </Card>;
}
