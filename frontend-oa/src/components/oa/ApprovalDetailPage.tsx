'use client';

import { useMemo } from 'react';
import { useRouter, useSearchParams } from '@/lib/nextCompat';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Descriptions,
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

export default function ApprovalDetailPage({ taskId }: { taskId: number }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useTranslation();
  const fromMyApplications = searchParams.get('from') === 'my-applications';
  const fromTodo = searchParams.get('from') === 'todo';
  const fromDashboard = searchParams.get('from') === 'dashboard';
  const detail = useApprovalTaskDetail(taskId);
  const { application, timeline, loading, error, load } = detail;

  const { target: returnTarget, label: returnLabel } = useMemo(() => {
    if (fromDashboard) return { target: '/oa/dashboard', label: t('approval.approvalDetail.backToDashboard') };
    if (fromTodo) return { target: '/oa/todo', label: t('approval.approvalDetail.backToTodo') };
    if (fromMyApplications) return { target: '/oa/my-applications', label: t('approval.approvalDetail.backToMyApplications') };
    if (searchParams.get('from') === 'approval-list') {
      return { target: '/oa/approval-list', label: t('approval.approvalDetail.backToApprovalList') };
    }
    return application?.canApprove
      ? { target: '/oa/todo', label: t('approval.approvalDetail.backToTodo') }
      : { target: '/oa/my-applications', label: t('approval.approvalDetail.backToMyApplications') };
  }, [fromDashboard, fromTodo, fromMyApplications, application?.canApprove, searchParams, t]);

  return (
    <section className="approval-workbench">
      <header className="approval-detail-hero">
        <Button
          type="text"
          icon={<OaIcon name="previous" />}
          onClick={() => router.push(returnTarget)}
        >
          {returnLabel}
        </Button>
        <div className="approval-detail-hero__title">
          <span>{t('approval.approvalDetail.kicker', { id: taskId })}</span>
          <Typography.Title level={2}>{t('approval.approvalDetail.title')}</Typography.Title>
        </div>
        {application && <StatusTag status={application.status} />}
      </header>

      {error != null && <Alert showIcon type="error" message={formatOaApiError(error)}
        action={<Button onClick={() => void load()}>{t('common.retry')}</Button>} />}
      <Spin spinning={loading}>
        {!application ? (
          <Card className="leave-empty-card">
            <Empty description={t('approval.approvalDetail.empty')} />
          </Card>
        ) : (
          <div className="approval-detail-layout">
            <main className="approval-detail-main">
              <Card className="approval-summary-card" variant="borderless">
                <div className="approval-applicant">
                  <Avatar size={54} src={application.applicantAvatarUrl || undefined} icon={<OaIcon name="user" />} />
                  <div>
                    <Typography.Text type="secondary">{t('approval.approvalDetail.applicantLabel')}</Typography.Text>
                    <Typography.Title level={4}>{application.applicantName}</Typography.Title>
                    <Typography.Text type="secondary">
                      {t('approval.approvalDetail.submittedAt', { time: formatDateTime(application.submittedAt || application.createdAt) })}
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

                <Descriptions
                  className="approval-business-details"
                  column={{ xs: 1, sm: 2 }}
                  bordered
                >
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
                  <Descriptions.Item label={t('approval.approvalDetail.descApprover')}>
                    <Space>
                      <Avatar size="small" src={application.approverAvatarUrl || undefined} icon={<OaIcon name="user" />} />
                      {application.approverName || t('approval.approvalDetail.approverUnset')}
                    </Space>
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descDueAt')}>
                    {application.taskDueAt ? formatDateTime(application.taskDueAt) : '-'}
                    {application.overdue && <Tag color="error">{t('approval.approvalDetail.overdue')}</Tag>}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descReminder')}>
                    {t('approval.approvalDetail.reminderCount', { count: application.reminderCount })}
                    {application.lastRemindedAt && (
                      <Typography.Text type="secondary">
                        {' · '}{formatDateTime(application.lastRemindedAt)}
                      </Typography.Text>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label={t('approval.approvalDetail.descReason')} span={2}>
                    <Typography.Paragraph className="approval-reason">
                      {application.reason}
                    </Typography.Paragraph>
                  </Descriptions.Item>
                </Descriptions>
              </Card>

              <Card
                className="approval-history-card"
                variant="borderless"
                title={(
                  <div className="leave-card-title">
                    <OaIcon name="history" />
                    <span>{t('approval.approvalDetail.historyTitle')}</span>
                    <Tag bordered={false}>{t('approval.approvalDetail.historyCount', { count: timeline.length })}</Tag>
                  </div>
                )}
              >
                {timeline.length === 0 ? (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('approval.approvalDetail.historyEmpty')} />
                ) : (
                  <Timeline
                    items={timeline.map((item) => ({
                      color: timelineColor(item.toStatus),
                      dot: <span className={`approval-timeline-dot is-${item.toStatus.toLowerCase()}`} />,
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
                          {item.comment && (
                            <blockquote>{item.comment}</blockquote>
                          )}
                          {item.targetUserName && (
                            <Typography.Text type="secondary">
                              {t('approval.approvalDetail.participantChange', {
                                from: item.originalAssigneeName || item.actorName,
                                to: item.targetUserName,
                              })}
                            </Typography.Text>
                          )}
                        </article>
                      ),
                    }))}
                  />
                )}
              </Card>

              <ApprovalTaskActions application={application} scope={detail.scope} isCurrent={detail.isCurrent}
                loading={loading} reload={load} onMoved={() => router.push('/oa/todo')} />
            </main>

            <LeaveWorkflowPanel
              application={application}
              durationDays={application.durationDays}
            />
          </div>
        )}
      </Spin>

    </section>
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
