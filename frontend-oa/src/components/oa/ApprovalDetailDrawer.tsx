'use client';

import { useMemo } from 'react';
import { useRouter } from '@/lib/nextCompat';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Space,
  Spin,
  Tag,
  Timeline,
  Typography,
} from 'antd';
import dayjs from 'dayjs';
import { formatOaApiError } from '@/lib/oaApi';
import { useApprovalTaskDetail } from '@/hooks/useApprovalTaskDetail';
import ApprovalTaskActions from './ApprovalTaskActions';
import { OaIcon } from '@/components/OaIcon';
import { leaveTypeLabel, periodLabel, StatusTag } from './MyApplicationsPage';
import LeaveWorkflowPanel from './LeaveWorkflowPanel';
import { useTranslation } from 'react-i18next';
import i18n from '@/i18n';

/**
 * 审批中心列表行快速预览 Drawer：
 * 与详情路由页（/oa/approval-tasks/:id）展示同一套业务信息，
 * 支持审批操作；底部提供「查看完整详情」跳转独立路由页。
 */
export default function ApprovalDetailDrawer({
  taskId,
  open,
  onClose,
  onUpdated,
}: {
  taskId: number | null;
  open: boolean;
  onClose: () => void;
  onUpdated?: () => void;
}) {
  const router = useRouter();
  const { t } = useTranslation();
  const detail = useApprovalTaskDetail(taskId, open);
  const { application, timeline, loading, error, load } = detail;

  const businessInfo = useMemo(() => {
    if (!application) return null;
    return (
      <Descriptions className="approval-business-details" column={{ xs: 1, sm: 2 }} bordered size="small">
        <Descriptions.Item label={t('approval.approvalDetail.descApplicationNo')}>
          LV-{String(application.id).padStart(6, '0')}
        </Descriptions.Item>
        <Descriptions.Item label={t('approval.approvalDetail.descLeaveType')}>
          <Tag bordered={false}>{leaveTypeLabel(application.leaveType)}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('approval.approvalDetail.descStartTime')}>
          {application.startDate} {periodLabel(application.startPeriod)}
        </Descriptions.Item>
        <Descriptions.Item label={t('approval.approvalDetail.descEndTime')}>
          {application.endDate} {periodLabel(application.endPeriod)}
        </Descriptions.Item>
        <Descriptions.Item label={t('approval.approvalDetail.descApprover')} span={2}>
          <Space>
            <Avatar size="small" src={application.approverAvatarUrl || undefined} icon={<OaIcon name="user" />} />
            {application.approverName || t('approval.approvalDetail.approverUnset')}
          </Space>
        </Descriptions.Item>
        <Descriptions.Item label={t('approval.approvalDetail.descReason')} span={2}>
          <Typography.Paragraph className="approval-reason" style={{ marginBottom: 0 }}>
            {application.reason}
          </Typography.Paragraph>
        </Descriptions.Item>
      </Descriptions>
    );
  }, [application, t]);

  return (
    <>
      <Drawer
        className="approval-drawer"
        title={(
          <div className="approval-drawer__title">
            <span>{t('approval.approvalDetail.kicker', { id: taskId })}</span>
            <Typography.Text strong>{t('approval.approvalDetail.title')}</Typography.Text>
            {application && <StatusTag status={application.status} />}
          </div>
        )}
        width={560}
        open={open}
        onClose={onClose}
        extra={
          taskId != null && (
            <Button
              type="link"
              disabled={!application || loading}
              icon={<OaIcon name="more" />}
              onClick={() => {
                onClose();
                router.push(`/oa/approval-tasks/${taskId}?from=approval-list`);
              }}
            >
              {t('approval.approvalDetail.viewFullDetail')}
            </Button>
          )
        }
      >
        {error != null && <Alert type="error" showIcon message={formatOaApiError(error)}
          action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} />}
        <Spin spinning={loading}>
          {!application ? (
            <Empty description={t('approval.approvalDetail.empty')} />
          ) : (
            <div className="approval-drawer__body">
              <div className="approval-applicant">
                <Avatar size={46} src={application.applicantAvatarUrl || undefined} icon={<OaIcon name="user" />} />
                <div>
                  <Typography.Text type="secondary">{t('approval.approvalDetail.applicantLabel')}</Typography.Text>
                  <Typography.Title level={4} style={{ marginTop: 2, marginBottom: 0 }}>
                    {application.applicantName}
                  </Typography.Title>
                  <Typography.Text type="secondary">
                    {t('approval.approvalDetail.submittedAt', {
                      time: formatDateTime(application.submittedAt || application.createdAt),
                    })}
                  </Typography.Text>
                </div>
                <div className="approval-summary-card__duration">
                  <span>{t('approval.approvalDetail.durationLabel')}</span>
                  <strong>{application.durationDays}</strong>
                  <small>{t('approval.dayUnit')}</small>
                </div>
              </div>

              {!application.canApprove && application.status === 'PENDING' && (
                <Alert
                  className="leave-inline-alert"
                  showIcon
                  type="warning"
                  message={t('approval.approvalDetail.notAssigneeTitle')}
                  description={t('approval.approvalDetail.notAssigneeDesc')}
                />
              )}

              <Card className="oa-domain-card" variant="borderless" title={t('approval.approvalDetail.approvalInfoTitle')}>
                {businessInfo}
              </Card>

              <Card className="oa-domain-card" variant="borderless" title={t('approval.approvalDetail.flowTitle')}>
                <LeaveWorkflowPanel
                  application={application}
                  durationDays={application.durationDays}
                />
              </Card>

              <Card className="oa-domain-card" variant="borderless" title={t('approval.approvalDetail.recordsTitle')}>
                {timeline.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('approval.approvalDetail.historyEmpty')} />
                ) : (
                  <Timeline
                    items={timeline.map((item) => ({
                      color: timelineColor(item.toStatus),
                      children: (
                        <article className="approval-history-item">
                          <div className="approval-history-item__head">
                            <Typography.Text strong>{actionLabel(item.action)}</Typography.Text>
                            <Typography.Text type="secondary">
                              {formatDateTime(item.createdAt)}
                            </Typography.Text>
                          </div>
                          <Space size={8} wrap>
                            <Space size={6}>
                              <Avatar size="small" src={item.actorAvatarUrl || undefined} icon={<OaIcon name="user" />} />
                              <Tag bordered={false}>{item.actorName}</Tag>
                            </Space>
                            <Typography.Text type="secondary">
                              {statusLabel(item.fromStatus)} → {statusLabel(item.toStatus)}
                            </Typography.Text>
                          </Space>
                          {item.comment && <blockquote>{item.comment}</blockquote>}
                        </article>
                      ),
                    }))}
                  />
                )}
              </Card>

              <ApprovalTaskActions application={application} scope={detail.scope} isCurrent={detail.isCurrent}
                loading={loading} reload={load} onMoved={onClose} onUpdated={onUpdated} compact />
            </div>
          )}
        </Spin>
      </Drawer>

    </>
  );
}

function actionLabel(action: string) {
  const key = 'approval.action.' + action;
  const translated = i18n.t(key);
  return translated === key ? action : translated;
}

function statusLabel(status?: string) {
  if (!status) return i18n.t('approval.statusStart');
  const key = 'approval.status.' + status;
  const translated = i18n.t(key);
  return translated === key ? status : translated;
}

function timelineColor(status: string) {
  if (status === 'APPROVED') return 'green';
  if (status === 'REJECTED') return 'red';
  if (status === 'WITHDRAWN') return 'orange';
  return 'blue';
}

function formatDateTime(value?: string) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}
