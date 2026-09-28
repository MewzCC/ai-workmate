import { Descriptions, Space, Steps, Tag, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import type { AgentTaskStatus, AiTaskPlanResponse } from '@/types/oa';
import { agentToolTranslationKey, formatAgentPlanArgument, isSensitiveAgentPlanArgument } from '@/lib/agentToolPresentation';

interface AgentPlanPreviewProps {
  plan: AiTaskPlanResponse;
  status?: AgentTaskStatus;
  completedSteps?: number;
}

function statusColor(status: AgentTaskStatus): string {
  if (status === 'SUCCEEDED') return 'success';
  if (status === 'PARTIALLY_SUCCEEDED') return 'warning';
  if (['FAILED', 'TIMED_OUT', 'REJECTED', 'EXPIRED', 'CANCELLED'].includes(status)) return 'error';
  if (status === 'RUNNING') return 'processing';
  return 'default';
}

export default function AgentPlanPreview({ plan, status = plan.status, completedSteps = 0 }: AgentPlanPreviewProps) {
  const { t } = useTranslation();

  return (
    <div className="oa-ai-plan-preview">
      <div className="oa-ai-plan-heading">
        <Typography.Paragraph>{plan.summary}</Typography.Paragraph>
        <Space wrap>
          <Tag color={plan.riskLevel === 'L2' ? 'red' : plan.riskLevel === 'L1' ? 'gold' : 'green'}>
            {plan.riskLevel}
          </Tag>
          <Tag color={statusColor(status)}>{t(`oa.ai.status.${status}`)}</Tag>
          {plan.confirmationRequired ? <Tag color="warning">{t('oa.ai.requireConfirmTag')}</Tag> : null}
        </Space>
      </div>
      <Steps
        orientation="vertical"
        size="small"
        current={status === 'RUNNING' ? Math.max(0, completedSteps) : -1}
        items={plan.steps.map((step) => {
          const toolName = t(`aiPermission.tools.${agentToolTranslationKey(step.toolCode)}.name`, {
            defaultValue: step.title,
          });
          const argumentItems = Object.entries(step.arguments).map(([key, value]) => ({
            key,
            label: <Typography.Text code>{isSensitiveAgentPlanArgument(key) ? t('oa.ai.redactedArgumentLabel') : key}</Typography.Text>,
            children: <Typography.Text>{formatAgentPlanArgument(key, value, t('oa.ai.redactedArgument'))}</Typography.Text>,
          }));
          return {
            title: toolName,
            subTitle: <Typography.Text code>{step.toolCode}</Typography.Text>,
            content: argumentItems.length ? (
              <Descriptions size="small" column={1} items={argumentItems} />
            ) : (
              <Typography.Text type="secondary">{t('oa.ai.noPlanArguments')}</Typography.Text>
            ),
          };
        })}
      />
    </div>
  );
}
