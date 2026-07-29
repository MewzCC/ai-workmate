'use client';

import { Avatar, Badge, Divider, Progress, Space, Steps, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { OaIcon } from '@/components/OaIcon';
import type {
  LeaveApplication,
  LeaveApprovalContext,
  WorkflowStage,
} from '@/lib/oaApi';

interface LeaveWorkflowPanelProps {
  application?: LeaveApplication | null;
  context?: LeaveApprovalContext | null;
  durationDays?: number;
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
}: LeaveWorkflowPanelProps) {
  const applicantName = application?.applicantName || context?.applicantName || '当前申请人';
  const approverName = application?.approverName || context?.approverName;
  const stages = application?.workflowStages || previewStages(applicantName, approverName);
  const dueHours = context?.approvalDueHours || 48;

  return (
    <aside className="leave-workflow-panel" aria-label="审批流程预览">
      <div className="leave-workflow-panel__masthead">
        <div className="leave-workflow-panel__eyebrow">
          <OaIcon name="process" />
          <span>PROCESS CONTROL</span>
        </div>
        <Typography.Title level={4}>单级请假审批</Typography.Title>
        <Typography.Paragraph>
          直属审批人优先，部门默认审批人兜底。提交后生成唯一待办。
        </Typography.Paragraph>
      </div>

      <div className="leave-workflow-panel__identity">
        <Avatar size={44} icon={<OaIcon name="user" />} />
        <div>
          <Typography.Text type="secondary">申请人</Typography.Text>
          <Typography.Text strong>{applicantName}</Typography.Text>
          <Typography.Text type="secondary" className="leave-workflow-panel__meta">
            {[context?.departmentName, context?.positionName].filter(Boolean).join(' · ') || '组织信息以账号档案为准'}
          </Typography.Text>
        </div>
        <OaIcon name="next" className="leave-workflow-panel__arrow" />
        <Avatar size={44} icon={<OaIcon name="approval" />} />
        <div>
          <Typography.Text type="secondary">当前审批人</Typography.Text>
          <Typography.Text strong>{approverName || '待组织配置'}</Typography.Text>
          <Badge
            status={approverName ? 'success' : 'error'}
            text={approverName ? '审批链路可用' : '提交前需配置'}
          />
        </div>
      </div>

      <Divider />

      <Steps
        direction="vertical"
        size="small"
        current={Math.max(0, stages.findIndex((stage) => stage.status === 'PROCESS'))}
        items={stages.map((stage) => ({
          title: stage.title,
          status: statusMap[stage.status],
          description: (
            <div className="leave-workflow-stage">
              <span>{stage.description}</span>
              {(stage.actorName || stage.occurredAt) && (
                <Space size={6} wrap>
                  {stage.actorName && <Tag bordered={false}>{stage.actorName}</Tag>}
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
          <span>申请时长</span>
          <strong>{durationDays > 0 ? `${durationDays} 天` : '--'}</strong>
        </div>
        <div>
          <span>处理时限</span>
          <strong>{dueHours > 0 ? `${dueHours} 小时` : '未配置'}</strong>
        </div>
      </div>

      {application?.taskDueAt && (
        <div className="leave-workflow-panel__sla">
          <div>
            <Typography.Text strong>审批 SLA</Typography.Text>
            <Tag color={application.overdue ? 'error' : 'processing'}>
              {application.overdue ? '已超时' : '计时中'}
            </Tag>
          </div>
          <Progress
            percent={application.overdue ? 100 : slaPercent(application.submittedAt, application.taskDueAt)}
            status={application.overdue ? 'exception' : 'active'}
            showInfo={false}
            size="small"
          />
          <Typography.Text type="secondary">
            截止 {dayjs(application.taskDueAt).format('YYYY-MM-DD HH:mm')}
          </Typography.Text>
        </div>
      )}

      <div className="leave-workflow-panel__policy">
        <OaIcon name="lock" />
        <span>半天为最小单位 · 周末计入 · 审批结果全程留痕</span>
      </div>
    </aside>
  );
}

function previewStages(applicantName: string, approverName?: string): WorkflowStage[] {
  return [
    {
      key: 'APPLICATION',
      title: '填写并提交',
      status: 'PROCESS',
      actorName: applicantName,
      description: '完善申请信息并确认提交',
    },
    {
      key: 'APPROVAL',
      title: '直属/部门审批',
      status: 'WAIT',
      actorName: approverName,
      description: approverName ? '提交后由当前审批人处理' : '系统将在提交时校验审批人',
    },
    {
      key: 'COMPLETED',
      title: '流程归档',
      status: 'WAIT',
      description: '审批完成后自动记录结果',
    },
  ];
}

function slaPercent(submittedAt?: string, taskDueAt?: string): number {
  if (!submittedAt || !taskDueAt) return 0;
  const start = dayjs(submittedAt).valueOf();
  const end = dayjs(taskDueAt).valueOf();
  if (end <= start) return 100;
  return Math.max(0, Math.min(100, Math.round(((Date.now() - start) / (end - start)) * 100)));
}
