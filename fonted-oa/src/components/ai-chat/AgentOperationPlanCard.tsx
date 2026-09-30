import { Alert, App, Button, Card, Space, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import type { AiTaskPlanResponse } from '@/types/oa';
import AgentPlanPreview from '@/components/oa/AgentPlanPreview';
import { OaIcon } from '@/components/OaIcon';

interface AgentOperationPlanCardProps {
  plan: AiTaskPlanResponse;
  executing: boolean;
  onConfirm: () => Promise<void>;
  onDiscard: () => Promise<void>;
}
export default function AgentOperationPlanCard({ plan, executing, onConfirm, onDiscard }: AgentOperationPlanCardProps) {
  const { t } = useTranslation();
  const { modal } = App.useApp();

  const requestConfirm = () => {
    modal.confirm({
      title: t('chat.operationConfirmTitle'),
      content: <Space orientation="vertical" size={10}>
        <Typography.Text>{t('chat.operationConfirmContent', { riskLevel: plan.riskLevel })}</Typography.Text>
        <Typography.Text type="secondary">{plan.summary}</Typography.Text>
      </Space>,
      okText: t('chat.operationConfirm'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: plan.riskLevel === 'L2' },
      onOk: onConfirm,
    });
  };

  return (
    <Card className="ai-operation-plan" size="small" title={t('chat.operationPlanTitle')}>
      <Alert
        type={plan.riskLevel === 'L2' ? 'warning' : 'info'}
        showIcon
        title={t('chat.operationAwaitingConfirmation')}
        description={t('chat.operationGatewayHint')}
      />
      <AgentPlanPreview plan={plan} />
      <Space wrap>
        <Button
          type="primary"
          danger={plan.riskLevel === 'L2'}
          icon={<OaIcon name="ai" />}
          loading={executing}
          onClick={requestConfirm}
        >
          {t('chat.operationConfirm')}
        </Button>
        <Button disabled={executing} onClick={() => void onDiscard()}>{t('chat.operationDiscard')}</Button>
      </Space>
    </Card>
  );
}
