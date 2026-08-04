'use client';

import { Avatar, Badge, Divider, Progress, Space, Steps, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { OaIcon } from '@/components/OaIcon';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';
import type {
  ApproverCandidate,
  LeaveApplication,
  LeaveApprovalContext,
  WorkflowStage,
} from '@/lib/oaApi';

interface LeaveWorkflowPanelProps {
  application?: LeaveApplication | null;
  context?: LeaveApprovalContext | null;
  durationDays?: number;
  selectedApprover?: ApproverCandidate | null;
}

const statusMap: Record<WorkflowStage['status'], 'wait' | 'process' | 'finish' | 'error'> = {
  WAIT: 'wait',
  PROCESS: 'process',
  FINISH: 'finish',
  ERROR: 'error',
};

export default function LeaveWorkflowPanel({
  application,
  context,
  durationDays = 0,
  selectedApprover,
}: LeaveWorkflowPanelProps) {
  const { t } = useTranslation();
  const applicantName = application?.applicantName || context?.applicantName || t('approval.workflow.applicantFallback');
  const applicantAvatarUrl = application?.applicantAvatarUrl || context?.applicantAvatarUrl || undefined;
  const approverName = selectedApprover?.name || application?.approverName || context?.approverName;
  const approverAvatarUrl = selectedApprover?.avatarUrl || application?.approverAvatarUrl || undefined;
  const stages = application?.workflowStages || previewStages(applicantName, approverName);
  const dueHours = context?.approvalDueHours || 48;

  // SLA 状态：审批完成后冻结，避免进度条继续推进
  const finishStatus = application?.status;
  const isApproved = finishStatus === 'APPROVED';
  const isRejected = finishStatus === 'REJECTED';
  const isWithdrawn = finishStatus === 'WITHDRAWN';
  const isFinished = isApproved || isRejected || isWithdrawn;
  // 终态时以 completedAt 作为计算终点；若缺则回退到 taskDueAt，保证不再继续推进
  const slaFrozenAt = isFinished ? (application?.completedAt || application?.taskDueAt) : undefined;
  const slaPercentValue = application?.overdue
    ? 100
    : slaPercent(application?.submittedAt, application?.taskDueAt, slaFrozenAt);
  const slaTagColor = isApproved
    ? 'success'
    : isRejected
      ? 'error'
      : isWithdrawn
        ? 'warning'
        : application?.overdue
          ? 'error'
          : 'processing';
  const slaTagText = isApproved
    ? t('approval.workflow.slaApproved')
    : isRejected
      ? t('approval.workflow.slaRejected')
      : isWithdrawn
        ? t('approval.workflow.slaWithdrawn')
        : application?.overdue
          ? t('approval.workflow.slaOverdue')
          : t('approval.workflow.slaRunning');
  const slaProgressStatus: 'success' | 'exception' | 'active' = isApproved
    ? 'success'
    : (isRejected || application?.overdue)
      ? 'exception'
      : 'active';

  return (
    <aside className="leave-workflow-panel" aria-label={t('approval.workflow.ariaLabel')}>
      <div className="leave-workflow-panel__masthead">
        <div className="leave-workflow-panel__eyebrow">
          <OaIcon name="process" />
          <span>PROCESS CONTROL</span>
        </div>
        <Typography.Title level={4}>{t('approval.workflow.title')}</Typography.Title>
        <Typography.Paragraph>
          {t('approval.workflow.description')}
        </Typography.Paragraph>
      </div>

      <div className="leave-workflow-panel__identity">
        <Avatar size={44} src={applicantAvatarUrl} icon={<OaIcon name="user" />} />
        <div>
          <Typography.Text type="secondary">{t('approval.workflow.applicantLabel')}</Typography.Text>
          <Typography.Text strong>{applicantName}</Typography.Text>
          <Typography.Text type="secondary" className="leave-workflow-panel__meta">
            {[context?.departmentName, context?.positionName].filter(Boolean).join(' · ') || t('approval.workflow.orgInfoFallback')}
          </Typography.Text>
        </div>
        <OaIcon name="next" className="leave-workflow-panel__arrow" />
        <Avatar size={44} src={approverAvatarUrl} icon={<OaIcon name="approval" />} />
        <div>
          <Typography.Text type="secondary">{t('approval.workflow.approverLabel')}</Typography.Text>
          <Typography.Text strong>{approverName || t('approval.workflow.approverUnset')}</Typography.Text>
          <Badge
            status={approverName ? 'success' : 'error'}
            text={approverName ? t('approval.workflow.chainAvailable') : t('approval.workflow.chainNeedsConfig')}
          />
        </div>
      </div>

      <Divider />

      <Steps
        orientation="vertical"
        size="small"
        current={Math.max(0, stages.findIndex((stage) => stage.status === 'PROCESS'))}
        items={stages.map((stage) => ({
          title: stage.title,
          status: statusMap[stage.status],
          content: (
            <div className="leave-workflow-stage">
              <span>{stage.description}</span>
              {(stage.actorName || stage.occurredAt) && (
                <Space size={6} wrap>
                  {stage.actorName && <Tag variant="filled">{stage.actorName}</Tag>}
                  {stage.occurredAt && (
                    <Typography.Text type="secondary">
                      {dayjs(stage.occurredAt).format('MM-DD HH:mm')}
                    </Typography.Text>
                  )}
                </Space>
              )}
            </div>
          ),
        }))}
      />

      <div className="leave-workflow-panel__metrics">
        <div>
          <span>{t('approval.workflow.durationLabel')}</span>
          <strong>{durationDays > 0 ? t('approval.daysCount', { days: durationDays }) : '--'}</strong>
        </div>
        <div>
          <span>{t('approval.workflow.dueHoursLabel')}</span>
          <strong>{dueHours > 0 ? t('approval.hoursCount', { hours: dueHours }) : t('approval.workflow.dueHoursUnset')}</strong>
        </div>
      </div>

      {application?.taskDueAt && (
        <div className="leave-workflow-panel__sla">
          <div>
            <Typography.Text strong>{t('approval.workflow.slaLabel')}</Typography.Text>
            <Tag color={slaTagColor}>
              {slaTagText}
            </Tag>
          </div>
          <Progress
            percent={slaPercentValue}
            status={slaProgressStatus}
            showInfo={false}
            size="small"
          />
          <Typography.Text type="secondary">
            {t('approval.workflow.slaDeadline', { time: dayjs(application.taskDueAt).format('YYYY-MM-DD HH:mm') })}
          </Typography.Text>
        </div>
      )}

      <div className="leave-workflow-panel__policy">
        <OaIcon name="lock" />
        <span>{t('approval.workflow.policy')}</span>
      </div>
    </aside>
  );
}

function previewStages(applicantName: string, approverName?: string): WorkflowStage[] {
  return [
    {
      key: 'APPLICATION',
      title: i18n.t('approval.workflow.stageApplicationTitle'),
      status: 'PROCESS',
      actorName: applicantName,
      description: i18n.t('approval.workflow.stageApplicationDesc'),
    },
    {
      key: 'APPROVAL',
      title: i18n.t('approval.workflow.stageApprovalTitle'),
      status: 'WAIT',
      actorName: approverName,
      description: approverName
        ? i18n.t('approval.workflow.stageApprovalDescWithApprover')
        : i18n.t('approval.workflow.stageApprovalDescNoApprover'),
    },
    {
      key: 'COMPLETED',
      title: i18n.t('approval.workflow.stageCompletedTitle'),
      status: 'WAIT',
      description: i18n.t('approval.workflow.stageCompletedDesc'),
    },
  ];
}

function slaPercent(submittedAt?: string, taskDueAt?: string, frozenAt?: string): number {
  if (!submittedAt || !taskDueAt) return 0;
  const start = dayjs(submittedAt).valueOf();
  const end = dayjs(taskDueAt).valueOf();
  if (end <= start) return 100;
  // 终态时使用冻结时间点，避免进度条继续推进
  const now = frozenAt ? dayjs(frozenAt).valueOf() : Date.now();
  return Math.max(0, Math.min(100, Math.round(((now - start) / (end - start)) * 100)));
}
